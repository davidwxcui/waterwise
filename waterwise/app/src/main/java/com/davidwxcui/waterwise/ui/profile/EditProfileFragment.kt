package com.davidwxcui.waterwise.ui.profile

import android.Manifest
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.davidwxcui.waterwise.R
import com.davidwxcui.waterwise.databinding.FragmentEditProfileBinding
import kotlinx.coroutines.launch
import java.io.File

class EditProfileFragment : Fragment() {
    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!

    private var cameraTempUri: Uri? = null
    private var selectedAvatarUri: Uri? = null

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) setAvatar(cameraTempUri)
    }
    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) setAvatar(uri)
    }
    private val reqCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openCamera() else toast("Camera permission denied")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEditProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // spinners
        binding.spGender.adapter = ArrayAdapter.createFromResource(
            requireContext(), R.array.genders_simple, android.R.layout.simple_spinner_dropdown_item
        )
        binding.spActivity.adapter = ArrayAdapter.createFromResource(
            requireContext(), R.array.activity_levels, android.R.layout.simple_spinner_dropdown_item
        )
        binding.spActivityFreq.adapter = ArrayAdapter.createFromResource(
            requireContext(), R.array.activity_freq, android.R.layout.simple_spinner_dropdown_item
        )

        // load
        val cur = ProfilePrefs.load(requireContext())
        selectedAvatarUri = cur.avatarUri?.let { Uri.parse(it) }
        showAvatar(selectedAvatarUri)

        binding.etName.setText(cur.name)
        binding.etEmail.setText(cur.email)
        binding.etAge.setText(cur.age.toString())
        binding.etHeight.setText(cur.heightCm.toString())
        binding.etWeight.setText(cur.weightKg.toString())
        binding.spGender.setSelection(when (cur.sex) { Sex.MALE -> 0; Sex.FEMALE -> 1; else -> 0 })
        binding.spActivity.setSelection(when (cur.activity) {
            ActivityLevel.SEDENTARY -> 0; ActivityLevel.LIGHT -> 1; ActivityLevel.MODERATE -> 2;
            ActivityLevel.ACTIVE -> 3; ActivityLevel.VERY_ACTIVE -> 4
        })
        binding.spActivityFreq.setSelection(
            resources.getStringArray(R.array.activity_freq).indexOf(cur.activityFreqLabel).let { if (it>=0) it else 1 }
        )

        binding.etName.doAfterTextChanged {
            if (selectedAvatarUri == null) {
                val ch = it?.firstOrNull()?.uppercaseChar() ?: 'A'
                binding.tvAvatarBigInitial.text = ch.toString()
            }
        }

        val recalc = {
            val w = binding.etWeight.text.toString().toIntOrNull() ?: cur.weightKg
            val a = binding.etAge.text.toString().toIntOrNull() ?: cur.age
            val sex = if (binding.spGender.selectedItemPosition == 0) Sex.MALE else Sex.FEMALE
            val act = when (binding.spActivity.selectedItemPosition) {
                0 -> ActivityLevel.SEDENTARY; 1 -> ActivityLevel.LIGHT; 2 -> ActivityLevel.MODERATE;
                3 -> ActivityLevel.ACTIVE; else -> ActivityLevel.VERY_ACTIVE
            }
            val goal = HydrationFormula.dailyGoalMl(w.toFloat(), sex, a, act)
            binding.tvGoalPreview.text = "Goal: ${goal} ml"
        }
        binding.etWeight.doAfterTextChanged { recalc() }
        binding.etAge.doAfterTextChanged { recalc() }
        binding.spGender.onItemSelectedListener = simpleSelect { recalc() }
        binding.spActivity.onItemSelectedListener = simpleSelect { recalc() }
        recalc()

        // Change avatar
        binding.btnChangeAvatar.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Change avatar")
                .setItems(arrayOf("Take Photo", "Choose from Gallery")) { _, which ->
                    if (which == 0) {
                        if (ContextCompat.checkSelfPermission(
                                requireContext(), Manifest.permission.CAMERA
                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) reqCamera.launch(Manifest.permission.CAMERA)
                        else openCamera()
                    } else {
                        getContent.launch("image/*")
                    }
                }.show()
        }

        // Save
        binding.btnSave.setOnClickListener {
            val name = binding.etName.text?.toString()?.trim().orEmpty()
            val email = binding.etEmail.text?.toString()?.trim().orEmpty()
            val age = binding.etAge.text?.toString()?.toIntOrNull()
            val height = binding.etHeight.text?.toString()?.toIntOrNull()
            val weight = binding.etWeight.text?.toString()?.toIntOrNull()

            if (name.isEmpty()) { toast("Please enter name"); return@setOnClickListener }
            if (age == null || age !in 5..100) { toast("Age 5–100"); return@setOnClickListener }
            if (height == null || height !in 100..230) { toast("Height 100–230 cm"); return@setOnClickListener }
            if (weight == null || weight !in 25..250) { toast("Weight 25–250 kg"); return@setOnClickListener }

            val sex = if (binding.spGender.selectedItemPosition == 0) Sex.MALE else Sex.FEMALE
            val act = when (binding.spActivity.selectedItemPosition) {
                0 -> ActivityLevel.SEDENTARY; 1 -> ActivityLevel.LIGHT; 2 -> ActivityLevel.MODERATE;
                3 -> ActivityLevel.ACTIVE; else -> ActivityLevel.VERY_ACTIVE
            }
            val freq = binding.spActivityFreq.selectedItem?.toString() ?: "3-5 days/week"

            val pf = Profile(
                name = name, email = email, age = age, sex = sex,
                heightCm = height, weightKg = weight, activity = act,
                activityFreqLabel = freq, avatarUri = selectedAvatarUri?.toString()
            )

            // Save in local first，let Profile UI update
            ProfilePrefs.save(requireContext(), pf)

            // Update Firestore（users/{uid}）
            val uid = FirebaseAuthRepository.currentUid()
            if (uid.isNullOrBlank()) {
                toast("Saved locally (not logged in)")
                findNavController().popBackStack()
                return@setOnClickListener
            }

            binding.btnSave.isEnabled = false

            lifecycleScope.launch {
                val res = FirebaseAuthRepository.updateProfile(requireContext(), uid, pf)
                binding.btnSave.isEnabled = true

                if (res.isSuccess) {
                    toast("Saved & synced")
                    findNavController().popBackStack()
                } else {
                    toast(res.exceptionOrNull()?.message ?: "Sync failed, saved locally")
                    findNavController().popBackStack()
                }
            }
        }

        // Cancel
        binding.btnCancel.setOnClickListener { findNavController().popBackStack() }
    }

    private fun openCamera() {
        cameraTempUri = createTempImageUri()
        takePicture.launch(cameraTempUri)
    }

    private fun createTempImageUri(): Uri {
        val f = File.createTempFile("avatar_", ".jpg", requireContext().cacheDir)
        return FileProvider.getUriForFile(
            requireContext(), "${requireContext().packageName}.fileprovider", f
        )
    }

    private fun setAvatar(uri: Uri?) {
        selectedAvatarUri = uri
        showAvatar(uri)
    }

    private fun showAvatar(uri: Uri?) {
        if (uri != null) {
            binding.ivAvatar.setImageURI(uri)
            binding.ivAvatar.visibility = View.VISIBLE
            binding.tvAvatarBigInitial.visibility = View.GONE
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
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = block()
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
