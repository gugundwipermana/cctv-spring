/*
  ESP32-CAM - Capture & Upload ke Home Server (FastAPI)
  Board yang didukung: AI-Thinker ESP32-CAM (paling umum & murah)

  Cara pakai:
  1. Buka Arduino IDE -> Tools -> Board -> pilih "AI Thinker ESP32-CAM"
     (jika belum ada, install board package "esp32" by Espressif via Board Manager)
  2. Isi WIFI_SSID, WIFI_PASSWORD, dan SERVER_URL di bawah
  3. Sambungkan ESP32-CAM ke komputer via USB-to-TTL adapter (FTDI)
     - Saat upload, jumper pin GPIO0 ke GND, lalu tekan reset
     - Setelah selesai upload, lepas jumper GPIO0-GND, tekan reset lagi untuk jalan normal
  4. Tools -> Partition Scheme -> "Huge APP (3MB No OTA/1MB SPIFFS)"
  5. Upload sketch ini
*/

#include "esp_camera.h"
#include <WiFi.h>
#include <HTTPClient.h>

// ================= KONFIGURASI =================
const char *WIFI_SSID = "WIFI_USERNAME";
const char *WIFI_PASSWORD = "WIFI_PASSWORD";

// Ganti dengan IP PC home server kamu, port sesuai docker-compose Laravel (8000)
// Endpoint sekarang mengarah ke Laravel API (/api/upload), bukan langsung ke Python
const char *SERVER_URL = "http://192.168.1.3:8090/api/upload";

// Interval pengambilan gambar (ms). 1000 = 1 gambar/detik.
// Server akan menyimpan tiap frame ini dan menggabungkannya jadi 1 video per jam (24 video/hari).
// PC Ryzen 5600 + RX6600 cukup kuat untuk 1 fps ini. Kalau mau hemat bandwidth/disk, bisa dinaikkan ke 2000-3000ms.
const unsigned long CAPTURE_INTERVAL_MS = 1000;

// ID kamera (kalau nanti pasang lebih dari 1 ESP32-CAM, beri nama beda tiap kamera)
const char *CAMERA_ID = "cam1";

// ================= PIN CAMERA (AI-Thinker ESP32-CAM) =================
#define PWDN_GPIO_NUM     32
#define RESET_GPIO_NUM    -1
#define XCLK_GPIO_NUM      0
#define SIOD_GPIO_NUM     26
#define SIOC_GPIO_NUM     27
#define Y9_GPIO_NUM       35
#define Y8_GPIO_NUM       34
#define Y7_GPIO_NUM       39
#define Y6_GPIO_NUM       36
#define Y5_GPIO_NUM       21
#define Y4_GPIO_NUM       19
#define Y3_GPIO_NUM       18
#define Y2_GPIO_NUM        5
#define VSYNC_GPIO_NUM    25
#define HREF_GPIO_NUM     23
#define PCLK_GPIO_NUM     22

unsigned long lastCaptureTime = 0;

void connectWiFi() {
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Menyambungkan ke WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(300);
    Serial.print(".");
  }
  Serial.println();
  Serial.print("Terhubung! IP ESP32-CAM: ");
  Serial.println(WiFi.localIP());
}

bool initCamera() {
  camera_config_t config;
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer = LEDC_TIMER_0;
  config.pin_d0 = Y2_GPIO_NUM;
  config.pin_d1 = Y3_GPIO_NUM;
  config.pin_d2 = Y4_GPIO_NUM;
  config.pin_d3 = Y5_GPIO_NUM;
  config.pin_d4 = Y6_GPIO_NUM;
  config.pin_d5 = Y7_GPIO_NUM;
  config.pin_d6 = Y8_GPIO_NUM;
  config.pin_d7 = Y9_GPIO_NUM;
  config.pin_xclk = XCLK_GPIO_NUM;
  config.pin_pclk = PCLK_GPIO_NUM;
  config.pin_vsync = VSYNC_GPIO_NUM;
  config.pin_href = HREF_GPIO_NUM;
  config.pin_sscb_sda = SIOD_GPIO_NUM;
  config.pin_sscb_scl = SIOC_GPIO_NUM;
  config.pin_pwdn = PWDN_GPIO_NUM;
  config.pin_reset = RESET_GPIO_NUM;
  config.xclk_freq_hz = 20000000;
  config.pixel_format = PIXFORMAT_JPEG;

  // Resolusi: VGA (640x480) cukup untuk face detection & hemat bandwidth/CPU server.
  // Bisa dinaikkan ke FRAMESIZE_SVGA/UXGA jika perlu detail lebih tinggi.
  if (psramFound()) {
    config.frame_size = FRAMESIZE_VGA;
    config.jpeg_quality = 12; // makin kecil makin bagus kualitasnya (10-20 wajar)
    config.fb_count = 2;
  } else {
    config.frame_size = FRAMESIZE_QVGA;
    config.jpeg_quality = 15;
    config.fb_count = 1;
  }

  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    Serial.printf("Inisialisasi kamera gagal, error 0x%x\n", err);
    return false;
  }
  return true;
}

void captureAndUpload() {
  camera_fb_t *fb = esp_camera_fb_get();
  if (!fb) {
    Serial.println("Gagal mengambil frame dari kamera");
    return;
  }

  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("WiFi terputus, mencoba menyambung ulang...");
    esp_camera_fb_return(fb);
    connectWiFi();
    return;
  }

  HTTPClient http;
  http.begin(SERVER_URL);

  // Kirim sebagai multipart/form-data agar cocok dengan endpoint FastAPI (UploadFile)
  String boundary = "----ESP32CamBoundary";
  http.addHeader("Content-Type", "multipart/form-data; boundary=" + boundary);

  String bodyStart = "--" + boundary + "\r\n";
  bodyStart += "Content-Disposition: form-data; name=\"camera_id\"\r\n\r\n";
  bodyStart += String(CAMERA_ID) + "\r\n";
  bodyStart += "--" + boundary + "\r\n";
  bodyStart += "Content-Disposition: form-data; name=\"file\"; filename=\"capture.jpg\"\r\n";
  bodyStart += "Content-Type: image/jpeg\r\n\r\n";

  String bodyEnd = "\r\n--" + boundary + "--\r\n";

  size_t totalLen = bodyStart.length() + fb->len + bodyEnd.length();
  uint8_t *body = (uint8_t *)malloc(totalLen);
  if (!body) {
    Serial.println("Gagal alokasi memori untuk request");
    esp_camera_fb_return(fb);
    http.end();
    return;
  }

  size_t idx = 0;
  memcpy(body + idx, bodyStart.c_str(), bodyStart.length());
  idx += bodyStart.length();
  memcpy(body + idx, fb->buf, fb->len);
  idx += fb->len;
  memcpy(body + idx, bodyEnd.c_str(), bodyEnd.length());
  idx += bodyEnd.length();

  int httpResponseCode = http.POST(body, totalLen);

  if (httpResponseCode > 0) {
    Serial.printf("Upload sukses, response code: %d\n", httpResponseCode);
    String response = http.getString();
    Serial.println(response);
  } else {
    Serial.printf("Upload gagal, error: %s\n", http.errorToString(httpResponseCode).c_str());
  }

  free(body);
  http.end();
  esp_camera_fb_return(fb);
}

void setup() {
  Serial.begin(115200);
  Serial.setDebugOutput(false);

  if (!initCamera()) {
    Serial.println("Kamera gagal diinisialisasi, restart...");
    delay(3000);
    ESP.restart();
  }

  // Perbaiki orientasi gambar (sesuaikan 1/0 sampai hasilnya benar)
  sensor_t *s = esp_camera_sensor_get();
  s->set_vflip(s, 1);
  s->set_hmirror(s, 0);

  connectWiFi();
}

void loop() {
  unsigned long now = millis();
  if (now - lastCaptureTime >= CAPTURE_INTERVAL_MS) {
    lastCaptureTime = now;
    captureAndUpload();
  }
}
