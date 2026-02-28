package com.smartpillbox.app.data.remote

import android.content.Context
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.smartpillbox.app.data.local.AlarmEntity
import com.smartpillbox.app.data.local.MedicineEntity
import java.util.UUID

object FirebaseSyncManager {

    private const val PREFS_NAME = "smartpillbox_prefs"
    private const val KEY_PATIENT_CODE = "patient_code"
    private const val KEY_USER_ROLE = "user_role"

    private val db by lazy { FirebaseDatabase.getInstance() }

    fun generatePatientCode(): String {
        return UUID.randomUUID().toString().substring(0, 6).uppercase()
    }

    fun saveUserRole(context: Context, role: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USER_ROLE, role)
            .apply()
    }

    fun getUserRole(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_USER_ROLE, null)
    }

    fun registerPatientNode(patientCode: String, uid: String) {
        if (patientCode.isBlank()) return
        db.getReference("patients/$patientCode/uid").setValue(uid)
    }

    fun doesPatientExist(patientCode: String, callback: (Boolean) -> Unit) {
        if (patientCode.isBlank()) {
            callback(false)
            return
        }
        db.getReference("patients/$patientCode").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                callback(snapshot.exists())
            }
            override fun onCancelled(error: DatabaseError) {
                callback(false)
            }
        })
    }

    fun triggerBoxAction(patientCode: String, action: String) {
        if (patientCode.isBlank()) return
        val ref = db.getReference("patients/$patientCode/box_control")
        ref.child("command").setValue(action)
        ref.child("timestamp").setValue(System.currentTimeMillis())
    }

    fun syncMedicineToFirebase(patientCode: String, medicine: MedicineEntity) {
        if (patientCode.isBlank()) return
        val ref = db.getReference("patients/$patientCode/medicines")
        val key = if (medicine.firebaseKey.isNotBlank()) medicine.firebaseKey else ref.push().key ?: return
        ref.child(key).setValue(
            mapOf(
                "name" to medicine.name,
                "dosage" to medicine.dosage,
                "frequency" to medicine.frequency,
                "timing" to medicine.timing,
                "addedBy" to medicine.addedBy,
                "addedAt" to medicine.createdAt,
                "isActive" to medicine.isActive
            )
        )
    }

    fun syncAlarmToFirebase(patientCode: String, alarm: AlarmEntity) {
        if (patientCode.isBlank()) return
        val ref = db.getReference("patients/$patientCode/alarms")
        val key = ref.push().key ?: return
        ref.child(key).setValue(
            mapOf(
                "medicineId" to alarm.medicineId,
                "hour" to alarm.hour,
                "minute" to alarm.minute,
                "label" to alarm.label,
                "isEnabled" to alarm.isEnabled
            )
        )
    }

    fun listenForMedicineChanges(
        patientCode: String,
        callback: (List<MedicineEntity>) -> Unit
    ): ValueEventListener {
        val ref = db.getReference("patients/$patientCode/medicines")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val medicines = mutableListOf<MedicineEntity>()
                for (child in snapshot.children) {
                    val fbKey = child.key ?: continue
                    val name = child.child("name").getValue(String::class.java) ?: continue
                    val dosage = child.child("dosage").getValue(String::class.java) ?: ""
                    val frequency = child.child("frequency").getValue(String::class.java) ?: ""
                    val timing = child.child("timing").getValue(String::class.java) ?: "As directed"
                    val addedBy = child.child("addedBy").getValue(String::class.java) ?: ""
                    val createdAt = child.child("addedAt").getValue(Long::class.java) ?: System.currentTimeMillis()
                    medicines.add(
                        MedicineEntity(
                            name = name,
                            dosage = dosage,
                            frequency = frequency,
                            timing = timing,
                            addedBy = addedBy,
                            createdAt = createdAt,
                            ownerPatientCode = patientCode,
                            firebaseKey = fbKey
                        )
                    )
                }
                callback(medicines)
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun linkCaregiverToPatient(caregiverUid: String, patientCode: String) {
        if (patientCode.isBlank()) return
        db.getReference("patients/$patientCode/caregiver_uid").setValue(caregiverUid)
    }

    fun getPatientCode(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val code = prefs.getString(KEY_PATIENT_CODE, null)
        return if (code.isNullOrBlank()) null else code
    }

    fun savePatientCode(context: Context, code: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PATIENT_CODE, code)
            .apply()
    }
}