#include <SPI.h>
#include <LoRa.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include "time.h"

#define SS 5
#define RST 14
#define DIO0 2

// ----- Konfigurasi WiFi & Firestore -----
const char* ssid = "Volume";
const char* password = "hghv2081";
const String projectId = "smartpdamrev";
const String apiKey = "AIzaSyC3-KC-TAug-fthjHE46WHZL5XUdlev75E";

// ----- NTP Setup -----
const char* ntpServer = "pool.ntp.org";
const long  gmtOffset_sec = 8 * 3600;  // WITA
const int   daylightOffset_sec = 0;

// Struktur data LoRa
struct PutaranData {
  char rumah[10];
  int putaran;
};

// Helper: Format bulan_tahun, misal "07_25"
String getMonthYear() {
  struct tm timeinfo;
  if (!getLocalTime(&timeinfo)) {
    return "00_00";
  }
  char buf[6];
  snprintf(buf, sizeof(buf), "%02d_%02d", timeinfo.tm_mon + 1, (timeinfo.tm_year + 1900) % 100);
  return String(buf);
}

// Fungsi deklarasi
void printLocalTime();
void getFirestoreDocument(String rumah, String monthYear);

void setup() {
  Serial.begin(115200);

  // Koneksi WiFi
  WiFi.begin(ssid, password);
  Serial.print("Connecting to WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(300);
    Serial.print(".");
  }
  Serial.println("\nWiFi connected.");

  // Sinkronisasi waktu NTP
  configTime(gmtOffset_sec, daylightOffset_sec, ntpServer);
  delay(1000);
  printLocalTime();

  // Setup LoRa
  LoRa.setPins(SS, RST, DIO0);
  if (!LoRa.begin(433E6)) {
    Serial.println("LoRa tidak terhubung");
    while (1);
  }
  Serial.println("LoRa Initialized");
}

void loop() {
  // NTP periodik setiap 30 menit
  static unsigned long lastUpdate = 0;
  if (millis() - lastUpdate > 1800000) {
    lastUpdate = millis();
    printLocalTime();
  }

  // Cek data masuk dari LoRa 
  int packetSize = LoRa.parsePacket();
  if (packetSize) {
    // Membaca data yang diterima
    byte receivedBuffer[packetSize];
    LoRa.readBytes(receivedBuffer, packetSize);

    PutaranData receivedData;
    memcpy(&receivedData, receivedBuffer, sizeof(PutaranData));
    int rssi = LoRa.packetRssi();

    Serial.print("Data LoRa diterima: Rumah: ");
    Serial.print(receivedData.rumah);
    Serial.print(", Putaran: ");
    Serial.print(receivedData.putaran);
    Serial.print(", RSSI: ");
    Serial.println(rssi);

    // Ambil bulan_tahun lokal sekarang
    String monthYear = getMonthYear();

    // GET dokumen Firestore dengan rumah & bulan_tahun dari data LoRa & waktu NTP
    getFirestoreDocument(receivedData.rumah, monthYear);
  }
}

// Fungsi ambil waktu NTP
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

// Fungsi GET dokumen Firestore dinamis
void getFirestoreDocument(String rumah, String monthYear) {
  HTTPClient http;
  String firestorePath = "perumahan/griya_alam_sejati/rumah/" + rumah + "/konsumsi/" + monthYear;
  String url = "https://firestore.googleapis.com/v1/projects/" + projectId +
               "/databases/(default)/documents/" + firestorePath +
               "?key=" + apiKey;

  http.begin(url);
  int httpCode = http.GET();

  if (httpCode == 404) {
    //Dokumen bulan belum ada, buat baru 
    Serial.println("Dokumen bulan belum ada, membuat dokumen baru...");
    struct tm timeinfo;
    if (!getLocalTime(&timeinfo)) {
      Serial.println("Gagal mendapatkan waktu");
      http.end();
      return;
    }
    int hari = timeinfo.tm_mday;
    String hariStr = String(hari);

    // Buat payload dengan satu field tanggal hari ini
    String payload = "{ \"fields\": { \"" + hariStr + "\": { \"integerValue\": 10 } } }";

    // Buat dokumen baru dengan POST
    String urlBase = "https://firestore.googleapis.com/v1/projects/" + projectId +
                     "/databases/(default)/documents/perumahan/griya_alam_sejati/rumah/" + rumah + "/konsumsi" +
                     "?documentId=" + monthYear +
                     "&key=" + apiKey;
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
    //Dokumen sudah ada 
    String payload = http.getString();
    StaticJsonDocument<8192> doc;
    DeserializationError error = deserializeJson(doc, payload);
    if (!error) {
      JsonObject fields = doc["fields"];
      //Ambil field sesuai tanggal sekarang 
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
    Serial.printf("Data untuk tanggal %d: %d\n", hari, value);

    int newValue = value + 10;
    Serial.printf("Nilai baru untuk tanggal %d: %d (akan diupdate)\n", hari, newValue);
    updateFirestoreTodayValue(rumah, monthYear, hari, newValue);

  } else {
    Serial.printf("Tidak ada data untuk tanggal %d. Field akan dibuat!\n", hari);
    int newValue = 10;
    updateFirestoreTodayValue(rumah, monthYear, hari, newValue);
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

  // Payload hanya field hari yang diupdate/dibuat
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


