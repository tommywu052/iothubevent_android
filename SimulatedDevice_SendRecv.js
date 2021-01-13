'use strict';

var clientFromConnectionString = require('azure-iot-device-mqtt').clientFromConnectionString;
var Message = require('azure-iot-device').Message;

//var connectionString = 'HostName=a9IOT.azure-devices.net;DeviceId=a9device;SharedAccessKey=hRAXT2UdYZK4v/Nd1PpBPCqL0QKO9jk5Br8iFGhR1fk=';
//var connectionString = 'HostName=a9iothub.azure-devices.net;DeviceId=sphere01;SharedAccessKey=GU19dKWr4bs6P1Ko4w+zO3/lKcttgqFnBi1rX9//dP0=';
//var connectionString = 'HostName=iothub-vh6tl.azure-devices.net;DeviceId=murray0001;SharedAccessKey=H5TmzSiSbbsW2zscJ9IdJcSKNAcONL/UyOEtEEMdt14='
//var connectionString = 'HostName=iothub-f5wtx.azure-devices.net;DeviceId=xxxxxx;SharedAccessKey=MWiSEjDyZ5xYRzeEm3PQQsjO0YhAyC6GdKxjHqC5MiE='
//var connectionString = 'HostName=iotc-883775f4-5fb0-4212-8a39-8f533dd35bc7.azure-devices.net;DeviceId=tommydemo;SharedAccessKey=AM/7n4zUcpvme5AG3/fkIm6i0LxhAzL0leEXwyh/vuU='
//var connectionString   = 'HostName=iothub-nmquk.azure-devices.net;DeviceId=rmdevice;SharedAccessKey=z2h5Bd4lwkON/FW3v+jhwCWy03TkBEWq/Aa3hz6eTLU='
var connectionString   = 'HostName=a9iothub.azure-devices.net;DeviceId=android-event;SharedAccessKey=1g+gmxg4aS8PHUSHzmm23MPZdCboPPP5ehmYY4ADX8k='
var client = clientFromConnectionString(connectionString);


function printResultFor(op) {
  return function printResult(err, res) {
    if (err) console.log(op + ' error: ' + err.toString());
    if (res) console.log(op + ' status: ' + res.constructor.name);
  };
}

var connectCallback = function (err) {
  if (err) {
    console.log('Could not connect: ' + err);
  } else {
    console.log('Client connected');
    client.on('message', function (msg) {
      console.log('Id: ' + msg.messageId + ' Body: ' + msg.data);
      client.complete(msg, printResultFor('completed'));
    });
    // Create a message and send it to the IoT Hub every second
    setInterval(function () {
      var i = 0;
      //while (i<10){
      var temperature = 20 + (Math.random() * 15);
      var humidity = 60 + (Math.random() * 20);
      var data = JSON.stringify({ deviceId: 'a9device', temperature: temperature, humidity: humidity });
      //var data = Buffer.from('80', 'hex').toString();
      var message = new Message(data);
      message.properties.add('temperatureAlert', (temperature > 30) ? 'true' : 'false');
      console.log("Sending message: " + message.getData());
      client.sendEvent(message, printResultFor('send'));
      i++;
      // }
    }, 10000);
  }
};

client.open(connectCallback);
  //setInterval(function(){
  //  process.exit();
  //}, 3000);
