# Bitprix - Bitcoin Price Widget

Bitprix is a modern, lightweight Android application and home screen widget designed to track Bitcoin prices, market trends, and the Fear & Greed index in real-time.

## Features

- **Home Screen Widget**: Stay updated with Bitcoin's price directly from your home screen.
- **Multiple Currencies**: Supports over 50 fiat and cryptocurrencies (USD, EUR, GBP, BTC, etc.).
- **Interactive Price Chart**: View price history with multiple timeframes (1D, 1W, 1M, 6M, 1Y).
- **Fear & Greed Index**: Visual gauge to monitor market sentiment.
- **Auto-Refresh**: Background updates via WorkManager to keep data fresh.
- **Manual Refresh**: Force updates directly from the app or widget.
- **Chart Screenshots**: Save and share price charts with a single tap.
- **Material Design**: Clean, modern UI with support for system dark mode.

## Screenshots

*(Place screenshots here in the future)*

## Getting Started

### Prerequisites
- Android device running API 24 (Nougat) or higher.

### Installation
1. Clone the repository:
   ```bash
   git clone https://github.com/hypnoticHODL/bitprix.git
   ```
2. Open the project in Android Studio.
3. Build and run the app on your device or emulator.

## How to use the Widget
1. Long-press on your home screen.
2. Select **Widgets**.
3. Find **Bitprix** and drag it to your home screen.
4. Select your preferred currency in the configuration screen.

## Technologies Used
- **Kotlin**: Primary programming language.
- **Retrofit & OkHttp**: For network requests to CoinGecko and Fear & Greed APIs.
- **WorkManager**: For reliable background data syncing.
- **MPAndroidChart**: For rendering interactive price charts.
- **Coroutines & Lifecycle**: For efficient asynchronous operations.

## Data Sources
- Price and Chart Data: [CoinGecko API](https://www.coingecko.com/en/api)
- Market Sentiment: [Alternative.me Fear and Greed Index](https://alternative.me/crypto/fear-and-greed-index/)

## License
This project is licensed under the MIT License - see the LICENSE file for details.
