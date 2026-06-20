# Implementation Plan - Fixing Gallery Issues

This plan addresses several issues in the gallery application: fixing the empty gallery at startup, ensuring search history is saved, enabling the result grouping feature, and removing the non-functional "Portrait" filter.

## User Review Required

> [!IMPORTANT]
> - I will add a new "Group By" selector to the `SearchScreen` to allow users to use the grouping feature.
> - The "Portrait" filter will be completely removed as requested.

## Proposed Changes

### Data Layer

#### [MediaSearchProvider.kt](file:///D:/Users/ktb02865/AndroidStudioProjects/Gallery No AI/app/src/main/java/com/tama/gallerynoai/data/repository/MediaSearchProvider.kt)

- Remove `@Suppress("unused")` from `groupResults`.
- Remove `MediaType.PORTRAITS` case from `matchesType`.

#### [SearchModels.kt](file:///D:/Users/ktb02865/AndroidStudioProjects/Gallery No AI/app/src/main/java/com/tama/gallerynoai/data/model/SearchModels.kt)

- Remove `PORTRAITS` from `MediaType` enum.

---

### View Model Layer

#### [GalleryViewModel.kt](file:///D:/Users/ktb02865/AndroidStudioProjects/Gallery No AI/app/src/main/java/com/tama/gallerynoai/ui/viewmodel/GalleryViewModel.kt)

- **Fix 1 (Empty Gallery):** In `loadMedia()`, call `repository.syncMediaStoreWithRoom()` at the beginning of the `try` block (after setting `_isLoading.value = true`).
- **Fix 2 (Search History):**
    - Add `executeSearch(query: String)` that updates `_searchQuery` and calls `saveSearch(query)`.
- **Fix 3 (Grouping):**
    - Expose `searchOptions` as a `StateFlow`.
    - Add `setSearchGroupBy(groupBy: GroupBy?)`.
    - Add `searchGroupedResults` `StateFlow<Map<String, List<MediaItem>>?>`.
    - Update `filterMedia` to populate `_searchGroupedResults` if `groupBy` is active.

---

### UI Layer

#### [SearchScreen.kt](file:///D:/Users/ktb02865/AndroidStudioProjects/Gallery No AI/app/src/main/java/com/tama/gallerynoai/ui/screens/SearchScreen.kt)

- **Fix 2 (Search History):** Update `onSearch` and `onSuggestionClick` to use `viewModel.executeSearch`.
- **Fix 3 (Grouping):**
    - Add a horizontal row of filter chips for grouping (Daily, Monthly, Yearly, Extension, Type, Location).
    - Update `SearchResultsGrid` to display grouped results with headers when grouping is active.
- **Fix 4 (Remove Portrait):** I will check for any remaining filter buttons (if any exist) and ensure "Portrait" is gone. Since I didn't find them in the code yet, I'll ensure they are not added or removed if they were hidden in sub-components.

## Verification Plan

### Automated Tests
- I will run the existing search scoring tests to ensure no regressions.
- `gradlew test`

### Manual Verification
- **Startup Sync:** I will check the logs (or simulate) to ensure `syncMediaStoreWithRoom` is called.
- **Search History:** Perform a search and check if it appears in "Recent Searches" after clicking the search button.
- **Grouping:** Select different grouping options in the search screen and verify the results are correctly categorized with headers.
- **Portrait Filter:** Verify "Portrait" is no longer available in the code and UI.
