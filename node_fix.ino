#include <SPI.h>
#include <LoRa.h>

// Pin untuk Sensor Hall
const int hall1Pin = 6;  // Sensor Hall 1 terhubung ke pin D6
const int hall2Pin = 7;  // Sensor Hall 2 terhubung ke pin D7

// Struktur untuk data yang akan dikirim
struct PutaranData {
  char rumah[10];   // Nama rumah, misalnya "A_1"
  int putaran;      // Jumlah putaran
};

// Buffer untuk mengirim data
PutaranData data;

volatile bool isHall1Triggered = false;
int pulseCount = 1;

void setup() {
  Serial.begin(115200);
  pinMode(hall1Pin, INPUT);
  pinMode(hall2Pin, INPUT);

  // Setup interrupt untuk Hall 1 (menggunakan perubahan status dari HIGH ke LOW)
  //attachInterrupt(digitalPinToInterrupt(hall1Pin), hall1Interrupt, FALLING);  // Deteksi sinyal perubahan dari HIGH ke LOW

  // Setup LoRa
  if (!LoRa.begin(433E6)) {
    Serial.println("LoRa tidak terhubung");
    while (1);
  }
  Serial.println("LoRa Initialized");
}

void loop() {
  int hall1Value = digitalRead(hall1Pin);  // Membaca nilai dari Sensor Hall 1
  if (hall1Value == 0) {
    Serial.println(" Hall 1: " + hall1Value);
    int on = 0;
    while (on == 0) {  
    int hall2Value = digitalRead(hall2Pin);
    Serial.println(" Hall 2: " + hall2Value);
      if(hall2Value == 0){
      pulseCount++;  
      delay(2000);
      data.putaran = pulseCount;
      strcpy(data.rumah, "A_1");  
      sendData(data);  
      on = 1;
      }
    } 
  }
}

void hall1Interrupt() {
  isHall1Triggered = true;
}

// Fungsi untuk mengirim data struct
void sendData(PutaranData &data) {
  // Serialize data menjadi byte array
  byte dataBuffer[sizeof(PutaranData)];
  memcpy(dataBuffer, &data, sizeof(PutaranData));  // Menyalin data ke buffer

  // Kirim data menggunakan LoRa
  LoRa.beginPacket();
  LoRa.write(dataBuffer, sizeof(PutaranData));  // Kirim byte array
  LoRa.endPacket();

  // Debug: Menampilkan data yang dikirim
  Serial.print("Data dikirim: Rumah: ");
  Serial.print(data.rumah);
  Serial.print(", Putaran: ");
  Serial.println(data.putaran);
}
