
# react-native-esptouch2

## One should know that
This is a Unofficial project. The official demo is below:

[EsptouchForAndroid](https://github.com/EspressifApp/EsptouchForAndroid)

[EsptouchForIOS](https://github.com/EspressifApp/EsptouchForIOS)

## Getting started

`$ npm install react-native-esptouch2 --save`

### Mostly automatic installation

`$ react-native link react-native-esptouch2`

### Manual installation


#### iOS

1. In XCode, in the project navigator, right click `Libraries` ➜ `Add Files to [your project's name]`
2. Go to `node_modules` ➜ `react-native-esptouch2` and add `RNEsptouch.xcodeproj`
3. In XCode, in the project navigator, select your project. Add `libRNEsptouch.a` to your project's `Build Phases` ➜ `Link Binary With Libraries`
4. Run your project (`Cmd+R`)<

#### Android

1. Open up `android/app/src/main/java/[...]/MainActivity.java`
  - Add `import com.rickl.rn.esptouch.RNEsptouchPackage;` to the imports at the top of the file
  - Add `new RNEsptouchPackage()` to the list returned by the `getPackages()` method
2. Append the following lines to `android/settings.gradle`:
  	```
  	include ':react-native-esptouch2'
  	project(':react-native-esptouch2').projectDir = new File(rootProject.projectDir, 	'../node_modules/react-native-esptouch2/android')
  	```
3. Insert the following lines inside the dependencies block in `android/app/build.gradle`:
  	```
      compile project(':react-native-esptouch2')
  	```


## Additional react native modules needed for getting location service permission
react-native-permissions
react-native-android-location-service

## Usage
```javascript

import React, {Component} from 'react';
import {Platform, StyleSheet, Text, Button, View, DeviceEventEmitter} from 'react-native';
import RNEsptouch from 'react-native-esptouch2';
import Permissions from 'react-native-permissions';
import RNAndroidLocationService from 'react-native-android-location-service';

export default class App extends Component {
    constructor(props) {
        super(props);
        this.onPress = this.onPress.bind(this);
    }
    state = {
        lng: 0.0,
        lat: 0.0,
    };

    //request permission to access location
    requestPermission = () => {
        Permissions.request('location')
            .then(response => {
                //returns once the user has chosen to 'allow' or to 'not allow' access
                //response is one of: 'authorized', 'denied', 'restricted', or 'undetermined'
                this.setState({ locationPermission: response })
            });
    };

    onLocationChange(e) {
        this.setState({
            lng: e.Longitude,
            lat: e.Latitude
        });
    }

    componentDidMount() {
        this.requestPermission();
        if (!this.eventEmitter) {
            // Register Listener Callback - has to be removed later
            this.eventEmitter = DeviceEventEmitter.addListener('updateLocation', this.onLocationChange.bind(this));
            // Initialize RNGLocation
            if (Platform.OS === 'android') {
                RNAndroidLocationService.getLocation();
            }
        }
        RNEsptouch.initESPTouch();
    }

    componentWillUnmount() {
        // Stop listening for Events
        this.eventEmitter.remove();

        RNEsptouch.finish();
    }

    onPress() {
        let connected_wifi_password = "12345678";
        let broadcast_type = 1;	// 1: broadcast;	0: multicast
        RNEsptouch.startSmartConfig(connected_wifi_password, broadcast_type).then((res) => {
            if (res.code == 200) {
                // ESPTouch success
                console.log(res)
            } else {
                // ESPTouch failed
                console.info(res.msg)
            }
        })
    }

    render() {
        return (
            <View>
                <Text>Lng: {this.state.lng} Lat: {this.state.lat}</Text>
                <Button title="test" onPress={this.onPress} />
            </View>
        )
    }
}

```
## Licence
[Licence](https://github.com/EspressifApp/EsptouchForIOS/blob/master/ESPRESSIF_MIT_LICENSE_V1.LICENSE)
