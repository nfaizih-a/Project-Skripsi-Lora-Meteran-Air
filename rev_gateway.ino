#include <SPI.h>
#include <LoRa.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include "time.h"
#include <stdint.h>

#define SS 5
#define RST 14
#define DIO0 2

const char* ssid = "A70";
const char* password = "hghv2081";
const String projectId = "smartpdamrev";
const String apiKey = "AIzaSyC3-KC-TAug-fthjHE46WHZL5XUdlev75E";
const char* ntpServer = "pool.ntp.org";
const long  gmtOffset_sec = 8 * 3600;
const int   daylightOffset_sec = 0;

// ------------------------ STRUKTUR DATA ------------------------
struct PutaranData {
  char rumah[10];
  int16_t putaran;
  unsigned long timestamp;  // waktu kirim dari node
};

String getMonthYear() {
  struct tm timeinfo;
  if (!getLocalTime(&timeinfo)) return "00-00";
  char buf[6];
  snprintf(buf, sizeof(buf), "%02d-%02d", timeinfo.tm_mon + 1, (timeinfo.tm_year + 1900) % 100);
  return String(buf);
}

// ------------------------ FUNGSI SYNC --------------------------
void sendSyncToNode() {
  struct timeval tv;
  gettimeofday(&tv, NULL);  // waktu dari NTP
  unsigned long epochMillis = (unsigned long)tv.tv_sec * 1000 + (tv.tv_usec / 1000);  // dalam ms

  String syncMsg = "SYNC," + String(epochMillis);

  LoRa.beginPacket();
  LoRa.print(syncMsg);
  LoRa.endPacket();

  Serial.println("SYNC dikirim: " + syncMsg);
  LoRa.receive();
}

// ------------------------ SETUP -------------------------------
void setup() {
  Serial.begin(115200);
  WiFi.begin(ssid, password);
  Serial.print("Connecting to WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(300);
    Serial.print(".");
  }
  Serial.println("\nWiFi connected.");

  configTime(gmtOffset_sec, daylightOffset_sec, ntpServer);
  delay(1000);
  printLocalTime();

  LoRa.setPins(SS, RST, DIO0);
  if (!LoRa.begin(433E6)) {
    Serial.println("LoRa tidak terhubung");
    while (1);
  }
  LoRa.receive(); 
  Serial.println("LoRa Initialized");
}

// ------------------------ LOOP -------------------------------
void loop() {
  static unsigned long lastUpdate = 0;
  static unsigned long lastSyncTime = 0;

  // Periksa jika ada paket masuk
  int packetSize = LoRa.parsePacket();
  if (packetSize) {
    byte receivedBuffer[packetSize];
    LoRa.readBytes(receivedBuffer, packetSize);

    PutaranData receivedData;
    memcpy(&receivedData, receivedBuffer, sizeof(PutaranData));
    int rssi = LoRa.packetRssi();

    struct timeval tv;
    gettimeofday(&tv, NULL);
    unsigned long gwNow = (unsigned long)tv.tv_sec * 1000 + (tv.tv_usec / 1000);

    Serial.print("Data LoRa diterima: Rumah: ");
    Serial.print(receivedData.rumah);
    Serial.print(", Putaran: ");
    Serial.print(receivedData.putaran);
    Serial.print(", Timestamp: ");
    Serial.print(receivedData.timestamp);
    Serial.print(", RSSI: ");
    Serial.println(rssi);

    Serial.printf("Gateway now : %lu\n", gwNow);
    Serial.printf("Node timestamp : %lu\n", receivedData.timestamp);

    Serial.printf("Delay pengiriman ≈ %lu ms\n", gwNow - receivedData.timestamp);

    String monthYear = getMonthYear();
    getFirestoreDocument(receivedData.rumah, monthYear);

    lastSyncTime = millis();  // reset timer SYNC agar tidak langsung kirim lagi
    return;  // kembali supaya tidak lanjut ke SYNC
  }

  // Hanya kirim SYNC kalau sudah >15 detik & tidak ada data masuk
  if (millis() - lastSyncTime > 15000) {
    sendSyncToNode();
    lastSyncTime = millis();

    delay(50);        // beri waktu LoRa kembali idle (hindari tabrakan)
    LoRa.receive();   // pastikan kembali mode RX
  }

  // Cetak waktu lokal setiap 30 menit
  if (millis() - lastUpdate > 1800000) {
    lastUpdate = millis();
    printLocalTime();
  }
}

// ------------------------ CETAK WAKTU ------------------------
void printLocalTime() {
  struct tm timeinfo;
  if (!getLocalTime(&timeinfo)) {
    Serial.println("Failed to obtain time");
    return;
  }
  Serial.printf("Tanggal: %02d-%02d-%04d  Jam: %02d:%02d:%02d\n",
    timeinfo.tm_mday, timeinfo.tm_mon + 1, timeinfo.tm_year + 1900,
    timeinfo.tm_hour, timeinfo.tm_min, timeinfo.tm_sec);
}

// ---------------- FIRESTORE FUNGSI ----------------
void getFirestoreDocument(String rumah, String monthYear) {
  HTTPClient http;
  String firestorePath = "perumahan/griya_alam_sejati/rumah/" + rumah + "/konsumsi/" + monthYear;
  String url = "https://firestore.googleapis.com/v1/projects/" + projectId +
               "/databases/(default)/documents/" + firestorePath +
               "?key=" + apiKey;

  http.begin(url);
  int httpCode = http.GET();

  if (httpCode == 404) {
    Serial.println("Dokumen bulan belum ada, membuat dokumen baru...");
    struct tm timeinfo;
    if (!getLocalTime(&timeinfo)) {
      Serial.println("Gagal mendapatkan waktu");
      http.end();
      return;
    }
    int hari = timeinfo.tm_mday;
    String hariStr = String(hari);
    String payload = "{ \"fields\": { \"" + hariStr + "\": { \"integerValue\": 10 } } }";
    String urlBase = "https://firestore.googleapis.com/v1/projects/" + projectId +
                     "/databases/(default)/documents/perumahan/griya_alam_sejati/rumah/" + rumah + "/konsumsi" +
                     "?documentId=" + monthYear + "&key=" + apiKey;
    HTTPClient httpPost;
    httpPost.begin(urlBase);
    httpPost.addHeader("Content-Type", "application/json");
    int code = httpPost.POST(payload);
    if (code > 0) {
      String response = httpPost.getString();
      Serial.println("Dokumen bulan baru dibuat:");
      Serial.println(response);
    } else {
      Serial.printf("POST... failed, error: %s\n", httpPost.errorToString(code).c_str());
    }
    httpPost.end();

  } else if (httpCode > 0) {
    String payload = http.getString();
    StaticJsonDocument<8192> doc;
    DeserializationError error = deserializeJson(doc, payload);
    if (!error) {
      JsonObject fields = doc["fields"];
      getFirestoreTodayValue(fields, rumah, monthYear);
    }
  }
  http.end();
}

void getFirestoreTodayValue(JsonObject fields, String rumah, String monthYear) {
  struct tm timeinfo;
  if (!getLocalTime(&timeinfo)) {
    Serial.println("Gagal mendapatkan waktu");
    return;
  }
  int hari = timeinfo.tm_mday;
  String hariKey = String(hari);

  if (fields.containsKey(hariKey)) {
    int value = fields[hariKey]["integerValue"].as<int>();
    int newValue = value + 10;
    updateFirestoreTodayValue(rumah, monthYear, hari, newValue);
  } else {
    updateFirestoreTodayValue(rumah, monthYear, hari, 10);
  }
}

void updateFirestoreTodayValue(String rumah, String monthYear, int hari, int newValue) {
  HTTPClient http;
  String firestorePath = "perumahan/griya_alam_sejati/rumah/" + rumah + "/konsumsi/" + monthYear;
  String hariStr = String(hari);
  String url = "https://firestore.googleapis.com/v1/projects/" + projectId +
               "/databases/(default)/documents/" + firestorePath +
               "?updateMask.fieldPaths=`" + hariStr + "`" +
               "&key=" + apiKey;
  http.begin(url);
  http.addHeader("Content-Type", "application/json");
  String payload = "{ \"fields\": { \"" + hariStr + "\": { \"integerValue\": " + String(newValue) + " } } }";
  int httpCode = http.sendRequest("PATCH", payload);
  if (httpCode > 0) {
    String response = http.getString();
    Serial.println("UPDATE (PATCH) Response:");
    Serial.println(response);
  } else {
    Serial.printf("PATCH... failed, error: %s\n", http.errorToString(httpCode).c_str());
  }
  http.end();
}