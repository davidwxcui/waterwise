# waterwise

user-profile test
Show_and_Tell_1:
Kotlin / Logic

app/src/main/java/com/davidwxcui/waterwise/ui/profile/EditProfileFragment.kt
Edit screen logic: form validation, avatar change (camera/gallery), real-time daily hydration goal preview, Save/Cancel.

app/src/main/java/com/davidwxcui/waterwise/ui/profile/ProfileFragment.kt
Profile display logic: read data from local storage, render UI, compute & show daily goal; tap Edit to navigate to the edit screen.

app/src/main/java/com/davidwxcui/waterwise/ui/profile/ProfilePrefs.kt
SharedPreferences wrapper for reading/writing fields (name/email/age/sex/height/weight/activity/frequency/avatarUri).

Resources / UI

app/src/main/res/layout/fragment_edit_profile.xml
Edit screen layout (scrollable via NestedScrollView): Avatar + CHANGE, Name/Email/Age/Height/Weight, Gender/Activity/Frequency spinners, goal preview, Cancel/Save.

app/src/main/res/values/profilesave.xml
Dropdown arrays: genders_simple, activity_levels, activity_freq.

app/src/main/res/drawable/bg_avatar_gradient.xml
Circular gradient background for the avatar.

app/src/main/res/xml/file_paths.xml
FileProvider paths (store captured photos in cache).

Navigation / Manifest

app/src/main/res/navigation/mobile_navigation.xml
Added EditProfile destination and the Profile → Edit action.