"""
Smart Pillbox — Flask Server
Run: python server.py
"""

from flask import Flask, request, jsonify
from flask_cors import CORS
from apscheduler.schedulers.background import BackgroundScheduler
import websocket, json, logging
from datetime import datetime

# ============================================================
ESP32_IP   = "10.74.35.209"     # ESP32 IP (from Serial Monitor)
LAPTOP_IP  = "10.74.35.101"     # Your laptop IP
ESP32_WS   = f"ws://{ESP32_IP}:81"
FLASK_PORT = 5000
# ============================================================

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S"
)
log = logging.getLogger(__name__)

app = Flask(__name__)
CORS(app)

scheduler = BackgroundScheduler()
scheduler.start()

current_schedule = []

# ── Send session command to ESP32 via WebSocket ──
def fire_session(compartments: list, label: str = "scheduled"):
    log.info(f"[FIRE] {label} → doors {compartments}")
    try:
        ws = websocket.create_connection(ESP32_WS, timeout=5)
        msg = json.dumps({"cmd": "session", "doors": compartments})
        ws.send(msg)
        log.info(f"[FIRE] ✅ Sent to ESP32: {msg}")
        ws.close()
    except Exception as e:
        log.error(f"[FIRE] ❌ Failed to reach ESP32: {e}")

# ── Schedule all entries from app ──
def schedule_entries(entries: list):
    global current_schedule
    scheduler.remove_all_jobs()
    current_schedule = entries

    for entry in entries:
        t            = entry["time"]
        compartments = entry["compartments"]
        medicines    = entry.get("medicines", [])
        label        = entry.get("label", t)
        h, m         = t.split(":")

        scheduler.add_job(
            fire_session,
            'cron',
            hour=int(h),
            minute=int(m),
            args=[compartments, f"{label}@{t}"],
            id=f"job_{t}_{label}",
            replace_existing=True
        )
        log.info(f"[SCHEDULER] {label} {t} → doors {compartments} | meds: {medicines}")

    log.info(f"[SCHEDULER] ✅ {len(entries)} jobs scheduled")


# ============================================================
#  ROUTES
# ============================================================

@app.route("/upload", methods=["POST"])
def upload():
    data = request.json
    if not data or not isinstance(data, list):
        return jsonify({"status": "error", "msg": "Expected a JSON array"}), 400

    for i, entry in enumerate(data):
        if "time" not in entry or "compartments" not in entry:
            return jsonify({
                "status": "error",
                "msg": f"Entry {i} missing 'time' or 'compartments'"
            }), 400

    schedule_entries(data)
    log.info(f"[UPLOAD] ✅ Schedule received — {len(data)} entries")

    return jsonify({
        "status":   "ok",
        "jobs":     len(data),
        "schedule": data
    })


@app.route("/schedule", methods=["GET"])
def get_schedule():
    jobs = []
    for job in scheduler.get_jobs():
        jobs.append({
            "id":       job.id,
            "next_run": str(job.next_run_time)
        })
    return jsonify({
        "status":   "ok",
        "esp32":    ESP32_WS,
        "schedule": current_schedule,
        "jobs":     jobs
    })


@app.route("/fire", methods=["POST"])
def manual_fire():
    data = request.json
    compartments = data.get("compartments", [])
    if not compartments:
        return jsonify({"status": "error", "msg": "No compartments provided"}), 400

    fire_session(compartments, label="manual")
    return jsonify({"status": "ok", "compartments": compartments})


@app.route("/status", methods=["GET"])
def status():
    return jsonify({
        "status":     "online",
        "esp32":      ESP32_WS,
        "jobs_count": len(scheduler.get_jobs()),
        "time":       datetime.now().strftime("%H:%M:%S")
    })


# ============================================================
if __name__ == "__main__":
    log.info("=" * 50)
    log.info("       SMART PILLBOX SERVER")
    log.info("=" * 50)
    log.info(f"  ESP32 WebSocket : {ESP32_WS}")
    log.info(f"  Flask port      : {FLASK_PORT}")
    log.info(f"  Status URL      : http://{LAPTOP_IP}:{FLASK_PORT}/status")
    log.info(f"  Upload URL      : http://{LAPTOP_IP}:{FLASK_PORT}/upload")
    log.info(f"  Schedule URL    : http://{LAPTOP_IP}:{FLASK_PORT}/schedule")
    log.info("=" * 50)
    log.info("  Waiting for Android app to POST schedule...")
    log.info("=" * 50)
    app.run(host="0.0.0.0", port=FLASK_PORT, debug=False)