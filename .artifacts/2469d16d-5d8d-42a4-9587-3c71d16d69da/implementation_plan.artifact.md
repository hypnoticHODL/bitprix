# Fix Chart Timeframe Switching Bug

The regression where timeframe buttons (1W, 1M, 6M, 1Y) do not work is caused by the `MainActivity` local `fullYearChartData` variable not being initialized when the app starts in the default 1D view. This plan moves the timeframe and filtering logic into the `MainViewModel` to ensure consistent state management and fix the bug.

## User Review Required

> [!NOTE]
> This change further consolidates business logic into the `MainViewModel`, improving the MVVM architecture and making the chart behavior more predictable.

## Proposed Changes

### [Architecture & State Management]

#### [MODIFY] [MainViewModel.kt](file:///D:/Dev/bitcoin%20widget/app/src/main/java/io/github/hypnoticHODL/bitprix/ui/MainViewModel.kt)
- Update `MainUiState` to include `timeframe` and `displayChartData`.
- Add `setTimeframe(days: Int)` to handle timeframe changes.
- Implement `updateDisplayChart()` to perform filtering in the ViewModel.
- Ensure `loadData` triggers `updateDisplayChart()` upon completion.

#### [MODIFY] [MainActivity.kt](file:///D:/Dev/bitcoin%20widget/app/src/main/java/io/github/hypnoticHODL/bitprix/ui/MainActivity.kt)
- Remove local `currentDays` and `fullYearChartData` variables.
- Update `ChipGroup` listener to call `viewModel.setTimeframe(days)`.
- Update `observeViewModel` to react to `displayChartData` and `timeframe` changes.
- Update `updateChart` and `ChartMarkerView` to use `timeframe` from the UI state.
- Remove `filterAndDisplayChart()` from `MainActivity` as the logic is moved to the ViewModel.

## Verification Plan

### Automated Tests
- Build the project using `gradlew assembleDebug` to ensure no regression in compilation.

### Manual Verification
1.  **Timeframe Switching**: Launch the app and tap each timeframe button (1W, 1M, 6M, 1Y). Verify the chart updates correctly for each.
2.  **1D Toggle**: Switch back to 1D and verify the high-resolution 24h data is displayed.
3.  **Data Refresh**: Swipe to refresh while on a non-1D timeframe and verify the chart correctly re-renders the filtered data after the refresh completes.
4.  **Marker View**: Long-press on the chart in different timeframes and verify the date format in the marker (HH:mm for 1D, MMM dd for others).
