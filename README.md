# Chess Master BG — Android шахмат със Stockfish

Android приложение за игра срещу **Stockfish 18**, работещо офлайн, с настройка на силата (1320–3190 Elo), анализ на позиции, преглед на партията ход по ход, отмяна на ходове, автоматично съхраняване и споделяне на PGN.

## APK — инсталиране на телефона
**По-лесно:** след успешна компилация отвори [Releases](https://github.com/zivkoatanasov171-design/Chess-Master-BG---Android-Chess-with-Stockfish/releases), избери **Chess Master BG — Android APK** и изтегли **Chess-Master-BG.apk** директно, без разархивиране.\n\n**Резервен вариант (Actions):**\n1. Отвори раздел **Actions**: https://github.com/zivkoatanasov171-design/Chess-Master-BG---Android-Chess-with-Stockfish/actions
2. Избери последната успешна задача **Build Android APK**.
3. В раздел **Artifacts** свали **Chess-Master-BG-Android-APK**.
4. Разархивирай ZIP и инсталирай `app-debug.apk` на Android телефон с **ARM64**, Android 8 или по-нов.
5. При необходимост разреши инсталация от приложението за файлове. Това е debug APK, не е подписан за Google Play.

## Технически данни
Проект: Java / Android Gradle Plugin 8.7.3 / Gradle 8.10.2; библиотека [Chesslib 1.3.7](https://github.com/bhlangonijr/chesslib) за легалните ходове; официален [Stockfish 18](https://github.com/official-stockfish/Stockfish/releases/tag/sf_18) (двигателят се изтегля автоматично при компилация, не е качен в хранилището).

Stockfish е GPL-3.0. При разпространение спазвай GPL и предоставяй съответния изходен код: https://github.com/official-stockfish/Stockfish. Chesslib е Apache-2.0. Приложението е свободен изходен код.

## Важно
Инсталационният файл се появява в **Actions** едва след успешна компилация. Състоянието на build задачата е видимо в GitHub. Движенията се правят с два допира: фигура, след това поле. Позиционният анализ е около 3 секунди и показва оценка и главен вариант в UCI нотация.
