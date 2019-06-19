#import "ESPTouchTask.h"

#if __has_include("RCTBridgeModule.h")
#import "RCTBridgeModule.h"
#else
#import <React/RCTBridgeModule.h>
#endif

@interface EspTouchDelegateImpl : NSObject<ESPTouchDelegate>

@end

@interface ESPTouchHelper : NSObject
@property NSString * ssid;
@property NSString * bssid;
@property (atomic, strong) ESPTouchTask *_esptouchTask;
@property (nonatomic, strong) NSCondition *_condition;
@property (nonatomic, strong) EspTouchDelegateImpl *_esptouchDelegate;
@end

@interface RNEsptouch : NSObject <RCTBridgeModule>
@property ESPTouchHelper *helper;
@end
