# Fix Rename Functionality

The rename function is currently not working reliably for two main reasons:
1. **Sync Issue**: The app's internal database (Room) syncs with `MediaStore` by comparing `id` and `dateModified`. Since renaming a file often doesn't change its `dateModified` timestamp, the sync logic skips renamed files, leaving the old name in the database and UI.
2. **Robustness**: The rename operation doesn't explicitly handle Android 11+ (API 30+) permission requirements for modifications and doesn't ensure file extensions are preserved, which can lead to failures in `MediaStore`.

## Proposed Changes

### Data Layer

#### [MediaIdAndDate.kt](file:///D:/Users/ktb02865/AndroidStudioProjects/Gallery No AI/app/src/main/java/com/tama/gallerynoai/data/local/db/MediaIdAndDate.kt)
- Add `name` property to `MediaIdAndDate` to allow detecting name changes during sync.

#### [MediaDao.kt](file:///D:/Users/ktb02865/AndroidStudioProjects/Gallery No AI/app/src/main/java/com/tama/gallerynoai/data/local/db/MediaDao.kt)
- Update `getAllIdsAndDates` query to fetch the `name` column.

#### [MediaRepository.kt](file:///D:/Users/ktb02865/AndroidStudioProjects/Gallery No AI/app/src/main/java/com/tama/gallerynoai/data/repository/MediaRepository.kt)
- Update `fetchMediaIdsAndDates` to include `DISPLAY_NAME` in the projection.
- Update `syncMediaStoreWithRoom` to compare the name as well as `dateModified`.
- Improve `renameMedia` to:
    - Handle API 30+ permission requests using `MediaStore.createWriteRequest`.
    - Ensure file extensions are preserved if the user omits them.
    - Check if the update actually modified any rows.

### UI Layer

#### [MediaDetailScreen.kt](file:///D:/Users/ktb02865/AndroidStudioProjects/Gallery No AI/app/src/main/java/com/tama/gallerynoai/ui/screens/MediaDetailScreen.kt)
- Fix the premature `onRefresh()` call when renaming requires permission. The refresh should happen after the user grants permission and the rename is actually performed.

## Verification Plan

### Manual Verification
1. Open a media item in the gallery.
2. Use the rename option to change its name.
3. Verify that the name is updated in the UI immediately after confirmation (if no permission needed) or after granting permission.
4. Verify that the file on disk (checked via file manager or `adb shell`) actually has the new name.
5. Try renaming without providing an extension (e.g., "Image.jpg" -> "NewName") and verify the extension is preserved.
6. Test on Android 10 (API 29) and Android 11+ (API 30+) to ensure permission handling works correctly.
