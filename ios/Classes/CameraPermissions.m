//
//  CameraPermissions.m
//  camerawesome
//
//  Created by Dimitri Dessus on 23/07/2020.
//

#import "CameraPermissions.h"

@implementation CameraPermissions

+ (BOOL)checkPermissions {
    NSString *mediaType = AVMediaTypeVideo;
    AVAuthorizationStatus authStatus = [AVCaptureDevice authorizationStatusForMediaType:mediaType];
    
    return (authStatus == AVAuthorizationStatusAuthorized);
}

@end
