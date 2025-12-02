package com.davidwxcui.waterwise.ui.profile

import android.Manifest
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.davidwxcui.waterwise.R
import com.davidwxcui.waterwise.databinding.FragmentEditProfileBinding
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

class EditProfileFragment : Fragment() {
    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!

    private var cameraTempUri: Uri? = null
    private var selectedAvatarUri: Uri? = null
    private var newAvatarLocalUri: Uri? = null

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            if (ok) setAvatar(cameraTempUri)
        }

    private val getContent =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) setAvatar(uri)
        }

    private val reqCamera =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) openCamera() else toast("Camera permission denied")
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            initUi()
        } catch (e: Exception) {
            Log.e("EditProfileFragment", "onViewCreated failed", e)
            toast("Profile screen error: ${e.javaClass.simpleName}")
        }
    }

    private fun initUi() {
        binding.spGender.adapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.genders_simple,
            android.R.layout.simple_spinner_dropdown_item
        )
        binding.spActivity.adapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.activity_levels,
            android.R.layout.simple_spinner_dropdown_item
        )
        binding.spActivityFreq.adapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.activity_freq,
            android.R.layout.simple_spinner_dropdown_item
        )

        // Load Profile
        val cur = ProfilePrefs.load(requireContext())
        val isLoggedIn = FirebaseAuthRepository.currentUid() != null
        if (isLoggedIn) {
            binding.etEmail.isEnabled = false
            binding.etEmail.isFocusable = false
            binding.etEmail.isFocusableInTouchMode = false
        }

        selectedAvatarUri = cur.avatarUri?.let { raw ->
            if (raw.isBlank()) return@let null
            try {
                val uri = Uri.parse(raw)
                if (uri.scheme == "content" || uri.scheme == "file") {
                    try {
                        requireContext().contentResolver.openInputStream(uri)?.close()
                    } catch (se: SecurityException) {
                        Log.w("EditProfileFragment", "No permission for saved avatar uri, clearing", se)
                        return@let null
                    } catch (e: Exception) {
                        Log.w("EditProfileFragment", "Cannot open saved avatar uri, clearing", e)
                        return@let null
                    }
                }
                uri
            } catch (e: Exception) {
                Log.w("EditProfileFragment", "Bad avatar uri string, clearing", e)
                null
            }
        }

        if (selectedAvatarUri == null && cur.avatarUri != null) {
            ProfilePrefs.save(requireContext(), cur.copy(avatarUri = null))
        }

        try {
            showAvatar(selectedAvatarUri)
        } catch (se: SecurityException) {
            Log.w("EditProfileFragment", "SecurityException in showAvatar, clear avatar", se)
            selectedAvatarUri = null
            showAvatar(null)
            ProfilePrefs.save(requireContext(), cur.copy(avatarUri = null))
        }

        binding.etName.setText(cur.name)
        binding.etEmail.setText(cur.email)
        binding.etAge.setText(cur.age.toString())
        binding.etHeight.setText(cur.heightCm.toString())
        binding.etWeight.setText(cur.weightKg.toString())

        binding.spGender.setSelection(
            when (cur.sex) {
                Sex.MALE -> 0
                Sex.FEMALE -> 1
                else -> 0
            }
        )

        binding.spActivity.setSelection(
            when (cur.activity) {
                ActivityLevel.SEDENTARY -> 0
                ActivityLevel.LIGHT -> 1
                ActivityLevel.MODERATE -> 2
                ActivityLevel.ACTIVE -> 3
                ActivityLevel.VERY_ACTIVE -> 4
            }
        )

        val freqArray = resources.getStringArray(R.array.activity_freq)
        val freqIndex = freqArray.indexOf(cur.activityFreqLabel).let { idx ->
            if (idx in freqArray.indices) idx else 1
        }
        binding.spActivityFreq.setSelection(freqIndex)

        // Name / Email check
        binding.etName.doAfterTextChanged {
            if (selectedAvatarUri == null) {
                val ch = it?.firstOrNull()?.uppercaseChar() ?: 'A'
                binding.tvAvatarBigInitial.text = ch.toString()
            }

            val name = it?.toString()?.trim().orEmpty()
            if (name.isNotEmpty() && name.length !in 2..30) {
                binding.etName.error = "Name 2–30 chars"
            } else {
                binding.etName.error = null
            }
        }

        binding.etEmail.doAfterTextChanged {
            val email = it?.toString()?.trim().orEmpty()
            if (email.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.etEmail.error = "Invalid email"
            } else {
                binding.etEmail.error = null
            }
        }

        val goalPreviewView: TextView? =
            binding.root.findViewById(R.id.tvGoalPreview)

        val recalc = {
            val w = binding.etWeight.text.toString().toIntOrNull() ?: cur.weightKg
            val a = binding.etAge.text.toString().toIntOrNull() ?: cur.age
            val sex = if (binding.spGender.selectedItemPosition == 0) Sex.MALE else Sex.FEMALE
            val act = when (binding.spActivity.selectedItemPosition) {
                0 -> ActivityLevel.SEDENTARY
                1 -> ActivityLevel.LIGHT
                2 -> ActivityLevel.MODERATE
                3 -> ActivityLevel.ACTIVE
                else -> ActivityLevel.VERY_ACTIVE
            }
            val goal = HydrationFormula.dailyGoalMl(w.toFloat(), sex, a, act)
            goalPreviewView?.text = "Goal: ${goal} ml"
        }
        binding.etWeight.doAfterTextChanged { recalc() }
        binding.etAge.doAfterTextChanged { recalc() }
        binding.spGender.onItemSelectedListener = simpleSelect { recalc() }
        binding.spActivity.onItemSelectedListener = simpleSelect { recalc() }
        recalc()

        // Change Avatar
        binding.btnChangeAvatar.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Change avatar")
                .setItems(arrayOf("Take Photo", "Choose from Gallery")) { _, which ->
                    if (which == 0) {
                        if (ContextCompat.checkSelfPermission(
                                requireContext(),
                                Manifest.permission.CAMERA
                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            reqCamera.launch(Manifest.permission.CAMERA)
                        } else {
                            openCamera()
                        }
                    } else {
                        getContent.launch("image/*")
                    }
                }
                .show()
        }

        binding.btnSave.setOnClickListener {
            try {
                doSave(cur)
            } catch (e: Exception) {
                Log.e("EditProfileFragment", "doSave crashed", e)
                toast("Save error: ${e.javaClass.simpleName}")
            }
        }
        binding.btnCancel.setOnClickListener {
            findNavController().popBackStack()
        }
    }


    private fun doSave(cur: Profile) {
        val name = binding.etName.text?.toString()?.trim().orEmpty()

        // 👉 先判断是否登录
        val uid = FirebaseAuthRepository.currentUid()
        val isLoggedIn = uid != null

        // 👉 登录状态：强制用原来的 email；未登录：用输入框里的
        val email = if (isLoggedIn) {
            cur.email
        } else {
            binding.etEmail.text?.toString()?.trim().orEmpty()
        }

        val age = binding.etAge.text?.toString()?.toIntOrNull()
        val height = binding.etHeight.text?.toString()?.toIntOrNull()
        val weight = binding.etWeight.text?.toString()?.toIntOrNull()

        // Username format check
        if (name.length !in 2..30) {
            binding.etName.error = "Name 2–30 chars"
            toast("Name must be 2–30 characters")
            return
        }

        // 👉 只有“未登录”的时候才校验邮箱格式
        if (!isLoggedIn) {
            if (email.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.etEmail.error = "Invalid email"
                toast("Please enter a valid email")
                return
            }
        }

        if (age == null || age !in 5..100) {
            toast("Age 5–100")
            return
        }
        if (height == null || height !in 100..230) {
            toast("Height 100–230 cm")
            return
        }
        if (weight == null || weight !in 25..250) {
            toast("Weight 25–250 kg")
            return
        }

        val sex = if (binding.spGender.selectedItemPosition == 0) Sex.MALE else Sex.FEMALE
        val act = when (binding.spActivity.selectedItemPosition) {
            0 -> ActivityLevel.SEDENTARY
            1 -> ActivityLevel.LIGHT
            2 -> ActivityLevel.MODERATE
            3 -> ActivityLevel.ACTIVE
            else -> ActivityLevel.VERY_ACTIVE
        }
        val freq = binding.spActivityFreq.selectedItem?.toString() ?: cur.activityFreqLabel

        val baseProfile = Profile(
            name = name,
            email = email,
            age = age,
            sex = sex,
            heightCm = height,
            weightKg = weight,
            activity = act,
            activityFreqLabel = freq,
            avatarUri = cur.avatarUri
        )

        // ⚠️ 这里不要再重新声明 uid，直接用前面那个
        if (uid.isNullOrBlank()) {
            val localAvatarStr = newAvatarLocalUri?.toString() ?: cur.avatarUri
            val localProfile = baseProfile.copy(avatarUri = localAvatarStr)
            ProfilePrefs.save(requireContext(), localProfile)
            toast("Saved locally (not logged in)")
            findNavController().popBackStack()
            return
        }

        binding.btnSave.isEnabled = false

        lifecycleScope.launch {
            try {
                var finalProfile = baseProfile
                val sourceUri: Uri? = when {
                    newAvatarLocalUri != null -> newAvatarLocalUri
                    !cur.avatarUri.isNullOrBlank() -> {
                        val old = try { Uri.parse(cur.avatarUri) } catch (e: Exception) { null }
                        if (old != null && (old.scheme == "content" || old.scheme == "file")) old else null
                    }
                    else -> null
                }

                if (sourceUri != null) {
                    val uploadedUrl = uploadAvatarToFirebase(uid, sourceUri)
                    finalProfile = if (uploadedUrl != null) {
                        baseProfile.copy(avatarUri = uploadedUrl)
                    } else {
                        baseProfile.copy(avatarUri = sourceUri.toString())
                    }
                }

                ProfilePrefs.save(requireContext(), finalProfile)

                val res = FirebaseAuthRepository.updateProfile(requireContext(), uid, finalProfile)
                binding.btnSave.isEnabled = true

                if (res.isSuccess) {
                    toast("Saved & synced")
                    findNavController().popBackStack()
                } else {
                    toast(res.exceptionOrNull()?.message ?: "Sync failed, saved locally")
                    findNavController().popBackStack()
                }
            } catch (e: Exception) {
                binding.btnSave.isEnabled = true
                Log.e("EditProfileFragment", "updateProfile crashed", e)
                toast("Sync failed, saved locally")
                findNavController().popBackStack()
            }
        }
    }


    // Upload to Firebase Storage，return URL（if fail return null）
    private suspend fun uploadAvatarToFirebase(uid: String, uri: Uri): String? {
        return try {
            val storageRef = FirebaseStorage.getInstance()
                .reference
                .child("avatars/$uid.jpg")

            storageRef.putFile(uri).await()
            val url = storageRef.downloadUrl.await().toString()
            Log.d("EditProfileFragment", "Avatar uploaded: $url")
            url
        } catch (e: Exception) {
            Log.e("EditProfileFragment", "uploadAvatarToFirebase failed", e)
            null
        }
    }

    private fun openCamera() {
        try {
            cameraTempUri = createTempImageUri()
            takePicture.launch(cameraTempUri)
        } catch (e: Exception) {
            Log.e("EditProfileFragment", "openCamera failed", e)
            toast("Cannot open camera: ${e.javaClass.simpleName}")
        }
    }

    private fun createTempImageUri(): Uri {
        val f = File.createTempFile("avatar_", ".jpg", requireContext().cacheDir)
        return FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            f
        )
    }

    private fun setAvatar(uri: Uri?) {
        selectedAvatarUri = uri
        newAvatarLocalUri = uri
        showAvatar(uri)
    }

    private fun showAvatar(uri: Uri?) {
        if (uri != null) {
            binding.ivAvatar.visibility = View.VISIBLE
            binding.tvAvatarBigInitial.visibility = View.GONE

            try {
                Glide.with(this)
                    .load(uri)
                    .circleCrop()
                    .into(binding.ivAvatar)
            } catch (e: Exception) {
                Log.w("EditProfileFragment", "Glide failed, fallback to setImageURI", e)
                binding.ivAvatar.setImageURI(uri)
            }
        } else {
            binding.ivAvatar.setImageDrawable(null)
            binding.ivAvatar.visibility = View.GONE
            val ch = binding.etName.text?.firstOrNull()?.uppercaseChar() ?: 'A'
            binding.tvAvatarBigInitial.text = ch.toString()
            binding.tvAvatarBigInitial.visibility = View.VISIBLE
        }
    }

    private fun simpleSelect(block: () -> Unit) =
        object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) = block()

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
