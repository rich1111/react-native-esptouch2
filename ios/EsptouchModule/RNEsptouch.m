#import <React/RCTLog.h>

#import "RNEsptouch.h"
#import "ESPTouchResult.h"
#import "ESP_NetUtil.h"
#import "ESPTouchDelegate.h"
#import "ESPAES.h"

#import <SystemConfiguration/CaptiveNetwork.h>

// ====== 回调类，新建esptouchTask时用到 ===============

@implementation EspTouchDelegateImpl

// 每当一个设备配网成功，回调一次
-(void) onEsptouchResultAddedWithResult: (ESPTouchResult *) result
{
    RCTLog(@"EspTouchDelegateImpl onEsptouchResultAddedWithResult bssid: %@", result.bssid);
}

@end


@implementation ESPTouchHelper

-(id)init {
    self = [super init];
    self._condition = [[NSCondition alloc]init];
    self._esptouchDelegate = [[EspTouchDelegateImpl alloc]init];
    return self;
}


- (void) startSmartConfig: (NSString *)apPwd broadcastType:(NSNumber *)type
                 resolver: (RCTPromiseResolveBlock)resolve
{
    NSDictionary *netInfo = [self fetchNetInfo];
    NSString *apSsid = [netInfo objectForKey:@"SSID"];
    NSString *apBssid = [netInfo objectForKey:@"BSSID"];
    int taskCount = 1;
    BOOL broadcast = [type intValue] == 1 ? YES : NO; // 1: broadcast  0:  multicast
    
    RCTLog(@"ssid======>%@", apSsid);
    RCTLog(@"apBssid======>%@", apBssid);
    RCTLog(@"apPwd======>%@", apPwd);
    RCTLog(@"taskCount======>%d", taskCount);
    RCTLog(@"broadcast======>%@", broadcast ? @"broadcast" : @"multicast");
    
    if (apSsid == nil || apSsid == NULL) {
        RCTLog(@"======>no Wifi connection");
        NSDictionary *res = @{@"code":@"-3",@"msg":@"no Wifi connection"};
        resolve(res);
        return;
    }
    
    RCTLog(@"ESPTouch smartConfig...");
    dispatch_queue_t  queue = dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0);
    dispatch_async(queue, ^{
        RCTLog(@"ESPTouch do the execute work...");
        // execute the task
        NSArray *esptouchResultArray = [self executeForResultsWithSsid:apSsid bssid:apBssid password:apPwd taskCount:taskCount broadcast:broadcast];
        // show the result to the user in UI Main Thread
        dispatch_async(dispatch_get_main_queue(), ^{
            ESPTouchResult *firstResult = [esptouchResultArray objectAtIndex:0];
            // check whether the task is cancelled and no results received
            if (!firstResult.isCancelled)
            {
                if ([firstResult isSuc])
                {   // 配网成功
                    RCTLog(@"======>ESPTouch success");
                    NSDictionary *res = @{@"code":@"200",@"msg":@"ESPTouch success"};
                    resolve(res);
                }
                
                else
                {   // 配网失败
                   RCTLog(@"======>ESPTouch fail");
                    NSDictionary *res = @{@"code":@"0",@"msg":@"ESPTouch fail"};
                    resolve(res);
                }
            }
            
        });
    });
}

// 取消配置任务
- (void) cancel
{
    [self._condition lock];
    if (self._esptouchTask != nil)
    {
        [self._esptouchTask interrupt];
    }
    [self._condition unlock];
}

- (NSArray *) executeForResultsWithSsid:(NSString *)apSsid bssid:(NSString *)apBssid password:(NSString *)apPwd taskCount:(int)taskCount broadcast:(BOOL)broadcast
{
    [self._condition lock];
    self._esptouchTask = [[ESPTouchTask alloc]initWithApSsid:apSsid andApBssid:apBssid andApPwd:apPwd];
    // set delegate
    [self._esptouchTask setEsptouchDelegate:self._esptouchDelegate];
    [self._esptouchTask setPackageBroadcast:broadcast];
    [self._condition unlock];
    NSArray * esptouchResults = [self._esptouchTask executeForResults:taskCount];
    RCTLog(@"ESPTouch executeForResult() result is: %@",esptouchResults);
    return esptouchResults;
}

- (NSString *)fetchSsid
{
    NSDictionary *ssidInfo = [self fetchNetInfo];
    
    return [ssidInfo objectForKey:@"SSID"];
}

- (NSString *)fetchBssid
{
    NSDictionary *bssidInfo = [self fetchNetInfo];
    
    return [bssidInfo objectForKey:@"BSSID"];
}

// refer to http://stackoverflow.com/questions/5198716/iphone-get-ssid-without-private-library
- (NSDictionary *)fetchNetInfo
{
    NSArray *interfaceNames = CFBridgingRelease(CNCopySupportedInterfaces());
    //    NSLog(@"%s: Supported interfaces: %@", __func__, interfaceNames);
    
    NSDictionary *SSIDInfo;
    for (NSString *interfaceName in interfaceNames) {
        SSIDInfo = CFBridgingRelease(
                                     CNCopyCurrentNetworkInfo((__bridge CFStringRef)interfaceName));
        //        NSLog(@"%s: %@ => %@", __func__, interfaceName, SSIDInfo);
        
        BOOL isNotEmpty = (SSIDInfo.count > 0);
        if (isNotEmpty) {
            break;
        }
    }
    return SSIDInfo;
}
@end


@implementation RNEsptouch

- (dispatch_queue_t)methodQueue
{
    return dispatch_get_main_queue();
}

RCT_EXPORT_MODULE();


RCT_EXPORT_METHOD(initESPTouch) {
    [ESP_NetUtil tryOpenNetworkPermission];
    if (self.helper == nil) {
        self.helper = [[ESPTouchHelper alloc] init];
    }
}

RCT_REMAP_METHOD(startSmartConfig,
                  password: (NSString *)pwd
                  broadcastType: (nonnull NSNumber *) type
                  resolver: (RCTPromiseResolveBlock)resolve
                 rejecter:(RCTPromiseRejectBlock)reject)
{
    if (self.helper == nil) {
        self.helper = [[ESPTouchHelper alloc] init];
    }
    [self.helper startSmartConfig:pwd broadcastType:type resolver: resolve];
}

RCT_REMAP_METHOD(getNetInfo,
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
    NSDictionary *netInfo = [self.helper fetchNetInfo];
    NSString *apSsid = [netInfo objectForKey:@"SSID"];
    NSString *apBssid = [netInfo objectForKey:@"BSSID"];
    apSsid = apSsid == nil ? @"" : apSsid;
    apBssid = apBssid == nil ? @"" : apBssid;
    NSDictionary *res = @{@"ssid":apSsid,@"bssid":apBssid};
    resolve(res);
}


RCT_EXPORT_METHOD(finish){
    
}

@end
