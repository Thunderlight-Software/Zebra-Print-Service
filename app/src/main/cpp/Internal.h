#ifndef ZEBRA_UTILS_INTERNAL_H
#define ZEBRA_UTILS_INTERNAL_H

#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>
#include <string>
#include <pthread.h>
#include <mutex>
#include <stdint.h>
#include <math.h>
#include <thread>
#include <zlib.h>
#include "base64.h"

//Globals
extern JavaVM* g_JVM;

#ifdef NDEBUG
#define ENGINE_VERSION                  "1.1.0.0 ( " __DATE__ " )"
#else
#define ENGINE_VERSION                  "1.1.0.0 ( " __DATE__ " ) - Debug"
#endif

//Useful defines
#define LOGE(...)						__android_log_print(ANDROID_LOG_ERROR, LOG_TAG,__VA_ARGS__)
#define LOGI(...)						__android_log_print(ANDROID_LOG_INFO, LOG_TAG,__VA_ARGS__)
#define SIZE(s)							(sizeof(s) / sizeof(s[0]))
#define ZPLHELPER_CLASS				    "com/zebra/zebraprintservice/service/ZebraPrintService"
#define LOG_TAG                         "Zebra-Utils"
#define RED(a)                          (((((a >> 11) & 0x1F) * 527) + 23) >> 6)
#define GREEN(a)                        (((((a >> 5) & 0x3F) * 259) + 33) >> 6)
#define BLUE(a)                         ((((a & 0x1F) * 527) + 23) >> 6)
#define AT(x,y)                         ((iWidth * (y)) + (x))

//ZPLHelper.cpp
bool ZPLconvertImage(AndroidBitmapInfo *info, void *pixels,  char **out);

//CPCHelper.cpp
bool CPCconvertImage(AndroidBitmapInfo *info, void *pixels,  char **out);


#endif //ZEBRA_UTILS_INTERNAL_H
