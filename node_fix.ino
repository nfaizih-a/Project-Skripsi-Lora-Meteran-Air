#include <SPI.h>
#include <LoRa.h>
#include <LowPower.h> 

// Pin untuk Sensor Hall
const int hall1Pin = 3; 
const int hall2Pin = 7;  

// Struktur untuk data yang akan dikirim
struct PutaranData {
  char rumah[10];   
  int putaran;      
};

// Buffer untuk mengirim data
PutaranData data;

volatile bool isHall1Triggered = false;
int pulseCount = 1;

void setup() {
  Serial.begin(115200);

  pinMode(hall1Pin, INPUT);
  pinMode(hall2Pin, INPUT);

  pinMode(13, OUTPUT);  
  digitalWrite(13, LOW);

  attachInterrupt(digitalPinToInterrupt(hall1Pin), hall1Interrupt, FALLING); 

  // Setup LoRa
  if (!LoRa.begin(433E6)) {
    Serial.println("LoRa tidak terhubung");
    while (1);
  }
  Serial.println("LoRa Initialized");

  //LoRa.idle();  
}

void loop() {
  int hall1Value = digitalRead(hall1Pin);  
  if (hall1Value == 0) {
    Serial.println(" Hall 1: " + hall1Value);
    int on = 0;
    while (on == 0) {  
    int hall2Value = digitalRead(hall2Pin);
    Serial.println(" Hall 2: " + hall2Value);
      if(hall2Value == 0){
      int pulseCount = 1;
      pulseCount++;  
      delay(2000);
      data.putaran = pulseCount;
      strcpy(data.rumah, "A_2");  
      sendData(data);  
      on = 1;
      }
    } 
    //LowPower.powerStandby(SLEEP_8S, ADC_ON, BOD_ON);
    //LowPower.powerSave(SLEEP_8S, ADC_ON, BOD_ON, TIMER2_ON);
    //LowPower.powerDown(SLEEP_FOREVER, ADC_ON, BOD_ON);
    
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

  
  LoRa.beginPacket();
  LoRa.write(dataBuffer, sizeof(PutaranData));  // Kirim byte array
  LoRa.endPacket();

  
  Serial.print("Data dikirim: Rumah: ");
  Serial.print(data.rumah);
  Serial.print(", Putaran: ");
  Serial.println(data.putaran);

  LoRa.sleep();
}
