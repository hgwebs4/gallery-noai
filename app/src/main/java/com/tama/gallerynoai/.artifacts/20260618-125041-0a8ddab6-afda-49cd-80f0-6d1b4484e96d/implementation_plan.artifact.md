# Rencana Pengembangan Personalisasi (System Bar Tinting & Grid Padding)

Pengembangan ini bertujuan untuk memberikan kontrol lebih kepada pengguna atas tampilan aplikasi Gallery No AI, mencakup tinting pada Status Bar dan Navigation Bar, serta pengaturan jarak (padding) antar foto pada grid.

## Proposed Changes

### [Settings & Data Layer]

Pembaruan pada `SettingsManager` untuk mendukung penyimpanan preferensi baru.

#### [SettingsManager.kt](file:///D:/Users/ktb02865/AndroidStudioProjects/Gallery No AI/app/src/main/java/com/tama/gallerynoai/data/settings/SettingsManager.kt)

- Tambahkan enum `SystemBarTint` dengan opsi `TRANSPARENT` dan `ACCENT_COLOR`.
- Tambahkan kunci DataStore: `KEY_STATUS_BAR_TINT`, `KEY_NAV_BAR_TINT`, dan `KEY_GRID_PADDING`.
- Implementasikan `StateFlow` dan metode setter untuk preferensi tersebut.

---

### [ViewModel Layer]

Mengekspos pengaturan baru ke UI.

#### [SettingsViewModel.kt](file:///D:/Users/ktb02865/AndroidStudioProjects/Gallery No AI/app/src/main/java/com/tama/gallerynoai/ui/viewmodel/SettingsViewModel.kt)

- Ekspos `statusBarTint`, `navBarTint`, dan `gridPadding`.
- Tambahkan metode untuk memperbarui nilai-nilai tersebut.

#### [GalleryViewModel.kt](file:///D:/Users/ktb02865/AndroidStudioProjects/Gallery No AI/app/src/main/java/com/tama/gallerynoai/ui/viewmodel/GalleryViewModel.kt)

- Ekspos `gridPadding` agar bisa digunakan di layar utama gallery.

---

### [UI Layer - Settings Screen]

Menambahkan kontrol UI di layar pengaturan.

#### [SettingsScreen.kt](file:///D:/Users/ktb02865/AndroidStudioProjects/Gallery No AI/app/src/main/java/com/tama/gallerynoai/ui/screens/SettingsScreen.kt)

- Tambahkan item pengaturan untuk:
    - **Status Bar Tint**: Dialog pilihan (Transparan / Ikuti Warna Aksen).
    - **Navigation Bar Tint**: Dialog pilihan (Transparan / Ikuti Warna Aksen).
    - **Grid Padding**: Dialog pilihan (Tanpa Jarak, Kecil (1dp), Sedang (2dp), Besar (4dp)).
- Gunakan `ListItem` dan `AlertDialog` yang konsisten dengan desain yang sudah ada.

---

### [System & Theme Integration]

Mengintegrasikan pengaturan sistem bar ke dalam Activity utama.

#### [MainActivity.kt](file:///D:/Users/ktb02865/AndroidStudioProjects/Gallery No AI/app/src/main/java/com/tama/gallerynoai/MainActivity.kt)

- Observasi perubahan pada `statusBarTint` dan `navBarTint`.
- Gunakan `enableEdgeToEdge` secara dinamis di dalam `LaunchedEffect` atau `SideEffect`.
- Logika penentuan warna bar: Jika `ACCENT_COLOR`, gunakan warna `primary` dari tema saat ini.

---

### [Grid Implementation]

Menerapkan padding dinamis pada grid media.

#### [MediaGridScreen.kt](file:///D:/Users/ktb02865/AndroidStudioProjects/Gallery No AI/app/src/main/java/com/tama/gallerynoai/ui/screens/MediaGridScreen.kt)
#### [GalleryScreen.kt](file:///D:/Users/ktb02865/AndroidStudioProjects/Gallery No AI/app/src/main/java/com/tama/gallerynoai/ui/screens/GalleryScreen.kt)
#### [SearchScreen.kt](file:///D:/Users/ktb02865/AndroidStudioProjects/Gallery No AI/app/src/main/java/com/tama/gallerynoai/ui/screens/SearchScreen.kt)

- Update `LazyVerticalGrid` untuk menggunakan nilai `gridPadding` dari ViewModel pada parameter `horizontalArrangement` dan `verticalArrangement`.

## Verification Plan

### Manual Verification
- **System Bar Tinting**:
    1. Buka Settings -> Appearance.
    2. Ganti Status Bar Tint ke "Ikuti Warna Aksen" dan verifikasi apakah Status Bar berubah warna mengikuti tema.
    3. Ganti ke "Transparan" dan verifikasi apakah Status Bar menjadi transparan (menunjukkan konten di bawahnya).
    4. Lakukan hal yang sama untuk Navigation Bar.
- **Grid Padding**:
    1. Buka Settings -> Appearance -> Grid Padding.
    2. Pilih "Tanpa Jarak" (0dp), kembali ke gallery, verifikasi foto-foto saling menempel rapat.
    3. Pilih "Besar" (4dp), kembali ke gallery, verifikasi adanya jarak yang jelas antar foto.
