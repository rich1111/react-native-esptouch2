
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


## Usage
```javascript
import RNEsptouch from 'react-native-esptouch2';

class Demo extends React.Component {
	constructor(props) {
		super(props);
		this.onPress = this.onPress.bind(this);
	}

	componentDidMount() {
		RNEsptouch.initESPTouch();
	}

	componentWillUnmount() {
		RNEsptouch.finish();
	}

	onPress() {
		let connected_wifi_password = "123456";
		let broadcast_type = 1;	// 1: broadcast;	0: multicast
		RNEsptouch.startSmartConfig(connected_wifi_password, broadcast_type).then((res) => {
			if (res.code == 200) {
				// ESPTouch success
			} else {
				// ESPTouch failed
				console.info(res.msg)
			}
		})
	}

	render() {
		return (
			<View>
				<Button title="test" onPress={this.onPress} />
			</View>
		)
	}
}

```
## Licence
[Licence](https://github.com/EspressifApp/EsptouchForIOS/blob/master/ESPRESSIF_MIT_LICENSE_V1.LICENSE)
