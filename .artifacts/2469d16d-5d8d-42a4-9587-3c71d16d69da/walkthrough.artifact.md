# Walkthrough - Chart Timeframe Switching Fix

I have fixed the bug where the timeframe buttons (1W, 1M, 6M, 1Y) were not updating the chart. The issue was caused by inconsistent state management between `MainActivity` and `MainViewModel`, specifically a local variable in the activity that wasn't being correctly initialized or updated.

## Changes Made

### 1. Unified State Management in `MainViewModel`
- Moved the `timeframe` (currentDays) selection and the filtering logic into the [MainViewModel.kt](file:///D:/Dev/bitcoin%20widget/app/src/main/java/io/github/hypnoticHODL/bitprix/ui/MainViewModel.kt).
- Updated `MainUiState` to include `timeframe` and `displayChartData`.
- Added a `setTimeframe(days: Int)` method that updates the selected timeframe and immediately recalculates the filtered `displayChartData`.

### 2. Reactive UI in `MainActivity`
- Refactored [MainActivity.kt](file:///D:/Dev/bitcoin%20widget/app/src/main/java/io/github/hypnoticHODL/bitprix/ui/MainActivity.kt) to remove local variables `currentDays` and `fullYearChartData`.
- The `ChipGroup` listener now simply calls `viewModel.setTimeframe(days)`.
- The UI now reactively observes `displayChartData` from the ViewModel's state. Whenever the timeframe changes or new data is fetched, the chart automatically updates.
- Updated the `updateChart` method and `ChartMarkerView` to use the `timeframe` from the UI state to determine the correct date formatting.

## Verification Results

### Automated Tests
- The project builds successfully (`gradlew assembleDebug`).

### Manual Verification Steps
1.  **Timeframe Toggle**: Open the app and tap "1W", "1M", etc. The chart should now correctly filter the 365-day data and refresh the view without an additional network request.
2.  **1D Resolution**: Tap "1D" and verify it shows the high-resolution 24-hour data fetched from the API.
3.  **Correct Labels**: Verify that the X-axis labels change from hourly (HH:mm) for 1D to daily (MMM dd) for other timeframes.
4.  **Marker View**: Tap on a point on the chart and verify the date format in the marker is correct for the selected timeframe.
