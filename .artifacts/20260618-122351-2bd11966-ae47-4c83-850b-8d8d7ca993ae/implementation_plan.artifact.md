# Implementation Plan - Fix Grid Columns Setting Inconsistency

The `gridColumns` setting is correctly saved and applied in some screens (Album Detail, Trash, Quick Access), but it is currently hardcoded to `3` in the main `GalleryScreen` and `SearchScreen`. This plan outlines the steps to make these screens respect the user's preference.

## Proposed Changes

### UI Screens

#### [GalleryScreen.kt](file:///D:/Users/ktb02865/AndroidStudioProjects/Gallery No AI/app/src/main/java/com/tama/gallerynoai/ui/screens/GalleryScreen.kt)
- Collect `gridColumns` from `SettingsViewModel` (via `GalleryViewModel`).
- Update `LazyVerticalGrid` to use the dynamic `gridColumns` value instead of a fixed `3`.

#### [SearchScreen.kt](file:///D:/Users/ktb02865/AndroidStudioProjects/Gallery No AI/app/src/main/java/com/tama/gallerynoai/ui/screens/SearchScreen.kt)
- Collect `gridColumns` from `SettingsViewModel` (via `GalleryViewModel`).
- Update `LazyVerticalGrid` in `SearchResultsGrid` to use the dynamic `gridColumns` value.

## Verification Plan

### Manual Verification
- Open the app and go to **Settings**.
- Change the **Grid Columns** setting (e.g., from 3 to 2 or 4).
- Navigate back to the **Photos** (Gallery) tab and verify the grid layout reflects the change.
- Go to the **Search** tab, perform a search, and verify the search results grid also reflects the change.
- Navigate to an **Album**, **Trash**, and **Quick Access** to ensure they still work as expected.
