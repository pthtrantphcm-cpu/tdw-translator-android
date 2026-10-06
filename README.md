# TDW Translator cho Android

Bản điện thoại của TDW AI Translator. Viết bằng Kotlin + Jetpack Compose, chạy trên Android 8.0 trở lên (Oppo ColorOS, Samsung, Xiaomi…).

## Có gì trong bản này

| Chức năng | Công nghệ trên điện thoại | Offline? |
|---|---|---|
| Dịch văn bản, tự nhận diện ngôn ngữ, sao chép, yêu thích | ML Kit Translation + Language ID | Có, sau khi tải gói ngôn ngữ (~30 MB/ngôn ngữ) |
| Dịch giọng nói: Start / Stop / Replay | Bộ nhận dạng giọng nói Android → ML Kit → TextToSpeech | Có, nếu đã tải gói giọng nói Google |
| Hội thoại 2 chiều VI↔EN / JA / ZH / KO, hiển thị dạng chat | như trên, mỗi người một nút nói | như trên |
| Dịch ảnh (chọn ảnh hoặc chụp), bản dịch đè lên ảnh + bảng song ngữ | ML Kit Text Recognition v2 | Có (model OCR nằm sẵn trong APK) |
| Dịch qua camera realtime, vẽ bản dịch lên khung hình | CameraX + ML Kit, OCR mỗi 1,2 giây | Có |
| Lịch sử, yêu thích, xóa | Room (SQLite) | Có |
| Cài đặt: tải/xóa gói ngôn ngữ, nghe thử giọng đọc | | |

### Khác bản Windows ở đâu, vì sao

- **Dịch dùng ML Kit thay cho NLLB-200.** NLLB 600M sau khi nén vẫn khoảng 600–900 MB và cần tự viết vòng giải mã bằng ONNX Runtime, nên chạy chậm trên điện thoại tầm trung. ML Kit cũng miễn phí, chạy trên máy và hỗ trợ 59 ngôn ngữ, đủ cho Việt/Anh/Nhật/Trung/Hàn. Chất lượng dịch câu dài thấp hơn NLLB một chút.
- **Giọng nói dùng bộ nhận dạng của Android thay cho Faster-Whisper.** Bộ này đã có sẵn trên máy, Android 12+ chạy được hẳn trên thiết bị. Vì nó cần biết trước ngôn ngữ, phần Hội thoại cho mỗi người một nút bấm thay vì tự đoán người nói.
- **Chưa có:** dịch PDF, phụ đề video realtime, AI Interpreter. Phụ đề bị hạn chế vì Netflix và nhiều app chặn thu âm thanh. AI Interpreter cần một LLM 1–2 GB chạy trên máy; tôi để dành cho bản sau.

## Cách 1: Android Studio trên Windows, cài thẳng vào Oppo qua cáp USB

1. Tải Android Studio: https://developer.android.com/studio (khoảng 1,2 GB). Cài theo mặc định, lần mở đầu chọn **Standard** để nó tải Android SDK.
2. **File → Open** → chọn thư mục `E:\Translator\tdw_translator_android`. Chờ dòng "Gradle sync" ở dưới chạy xong (lần đầu 5–15 phút vì tải thư viện).
3. Bật chế độ nhà phát triển trên Oppo:
   - Cài đặt → Giới thiệu về điện thoại → Phiên bản → chạm **Số phiên bản** 7 lần, nhập mật khẩu màn hình.
   - Cài đặt → Cài đặt bổ sung (Hệ thống) → Tùy chọn nhà phát triển → bật **Gỡ lỗi USB**.
   - ColorOS thường có thêm **Tắt giám sát quyền** và **Cài đặt qua USB**: bật cả hai, nếu không sẽ bị chặn cài.
4. Cắm cáp USB, trên điện thoại chọn **Truyền tệp** và bấm **Cho phép gỡ lỗi USB**.
5. Trong Android Studio, chọn tên điện thoại ở ô thiết bị trên thanh công cụ, bấm nút ▶ **Run**. App tự cài và mở.

Muốn có file APK để gửi cho đồng nghiệp: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**, file nằm ở `app\build\outputs\apk\debug\app-debug.apk`. Chép file vào điện thoại khác rồi mở để cài (cho phép "Cài ứng dụng không rõ nguồn gốc").

## Cách 2: không cài gì, để GitHub build hộ

1. Tạo repo mới trên github.com (để Private), tải toàn bộ thư mục này lên.
2. Vào tab **Actions** → **Build APK** → **Run workflow**. Khoảng 8–10 phút sau, mở lần chạy đó và tải `TDW-Translator-apk` ở mục **Artifacts**.
3. Giải nén, chép `app-debug.apk` vào điện thoại và cài.

## Lần đầu mở app

1. **Cài đặt** (bánh răng góc trên) → bấm **Tải** cho Tiếng Việt, Tiếng Anh và các ngôn ngữ cần dùng. Nên dùng Wi-Fi.
2. Giọng nói offline: mở app **Google** → ảnh đại diện → Cài đặt → Giọng nói → **Nhận dạng giọng nói khi không có mạng** → tải Tiếng Việt, English… Nếu máy Oppo thiếu mục này, cập nhật "Google" và "Speech Services by Google" trên CH Play.
3. Giọng đọc tiếng Việt: Cài đặt app → **Mở cài đặt đọc văn bản** → Google → Cài đặt dữ liệu giọng nói → Tiếng Việt.

Sau ba bước này, có thể bật chế độ máy bay để thử: dịch văn bản, ảnh, camera vẫn chạy được.

## iPhone

Muốn cài app lên iPhone thì bắt buộc phải build trên máy Mac có Xcode. Apple không cho build iOS trên Windows. Nếu có Mac, phần lõi dùng chung được hướng đi trong `docs/MOBILE.md` của bản Windows (Vision framework cho OCR, Apple Translation/ML Kit, AVSpeech). Tài khoản Apple miễn phí cài được lên máy của chính mình, mỗi 7 ngày phải cài lại.

## Cấu trúc mã nguồn

```
app/src/main/java/vn/thuducwater/translator/
├── TdwApp.kt                 AppContainer: DI thủ công (DB, engine)
├── MainActivity.kt           Thanh điều hướng 6 tab + Cài đặt
├── data/                     Languages, Room (History), Prefs
├── engine/                   TranslatorEngine (ML Kit), OcrEngine + renderOverlay, SpeechListener, TtsEngine
├── feature/<tên>/            Mỗi chức năng = ViewModel (logic) + Screen (Compose, chỉ hiển thị)
└── ui/                       Theme Sáng/Tối, LanguageButton, xin quyền
```

## Phiên bản thư viện

AGP 8.7.3 · Kotlin 2.0.21 · Gradle 8.10.2 · compileSdk 35 · minSdk 26 · Compose BOM 2024.12.01 · CameraX 1.4.1 · Room 2.6.1 · ML Kit translate 17.0.3 / text-recognition 16.0.1.

## Tình trạng kiểm thử

Mã nguồn đã qua kiểm tra cú pháp Kotlin (ktlint), nhưng chưa được biên dịch: môi trường soạn thảo không truy cập được kho thư viện Google Maven. Lần build đầu trên Android Studio có thể báo vài lỗi nhỏ. Chụp màn hình cửa sổ **Build** gửi lại để sửa.
