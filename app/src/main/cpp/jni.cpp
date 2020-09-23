#include "Internal.h"

//Globals
JavaVM* g_JVM = NULL;

//Forward References
jstring JNICALL JNI_getUtilsVersion(JNIEnv *JEnv, jclass JCls);
jstring JNICALL JNI_createBitmapZPL(JNIEnv *JEnv, jclass JCls, jobject bitmap);
jstring JNICALL JNI_createBitmapCPC(JNIEnv *JEnv, jclass JCls, jobject bitmap);

/***************************************************************************************/
/*  Signature                Java Type                                                 */
/*     Z                     boolean                                                   */
/*     B                     byte                                                      */
/*     C                     char                                                      */
/*     S                     short                                                     */
/*     I                     int                                                       */
/*     J                     long                                                      */
/*     F                     float                                                     */
/*     D                     double                                                    */
/*     L                     class                                                     */
/*     V                     void                                                      */
/*     [type                 array type                                                */
/*     (Args) Return Type    method type                                               */
/*                                                                                     */
/* (Ljava/lang/String;)Ljava/lang/String;                                              */
/* ([java/lang/String;)V                                                               */
/*                                                                                     */
/***************************************************************************************/
static JNINativeMethod sMethods[] =
        {
                /* name, 				 signature, 									    	                                                               funcPtr */
                { "getUtilsVersion",    "()Ljava/lang/String;",                                                                                                (void*)JNI_getUtilsVersion },
                { "createBitmapZPL",    "(Landroid/graphics/Bitmap;)Ljava/lang/String;",                                                                       (void*)JNI_createBitmapZPL },
                { "createBitmapCPC",    "(Landroid/graphics/Bitmap;)Ljava/lang/String;",                                                                       (void*)JNI_createBitmapCPC },

        };
/***************************************************************************************/
JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    JNIEnv* JEnv = NULL;
    jclass	    klass;
    g_JVM = vm;

    //Get JVM Pointer
    if (vm->GetEnv((void**)&JEnv, JNI_VERSION_1_6) != JNI_OK)
    {
        LOGE("GetEnv failed!");
        return -1;
    }

    //Find the class to register the natives against
    klass = JEnv->FindClass(ZPLHELPER_CLASS);

    if (klass == NULL)
    {
        LOGE("Unable to find class %s", ZPLHELPER_CLASS);
        return -1;
    }

    if (JEnv->RegisterNatives(klass, sMethods, SIZE(sMethods)))
    {
        LOGE("Register Natives Failed for class %s", ZPLHELPER_CLASS);
        return -1;
    }

    LOGI("**************************************************");
    LOGI("*             Zebra Utils Initialized            *");
    LOGI("*          (c)2020 Thunderlight Software         *");
    LOGI("**************************************************");
    LOGI("Version : %s",ENGINE_VERSION);
    return JNI_VERSION_1_6;
}
/***************************************************************************************/
jstring JNICALL JNI_getUtilsVersion(JNIEnv *JEnv, jclass JCls)
{
    return JEnv->NewStringUTF(ENGINE_VERSION);
}
/***************************************************************************************/
jstring JNICALL JNI_createBitmapZPL(JNIEnv *JEnv, jclass JCls, jobject bitmap)
{
    AndroidBitmapInfo info;
    int ret;
    void *pixels;
    char* retValue;

    //Get Bitmap Info
    if ((ret = AndroidBitmap_getInfo(JEnv, bitmap, &info)) < 0)
    {
        LOGE("AndroidBitmap_getInfo() failed ! error=%d", ret);
        return nullptr;
    }

    if ((ret = AndroidBitmap_lockPixels(JEnv, bitmap, &pixels)) < 0)
    {
        LOGE("AndroidBitmap_lockPixels() failed ! error=%d", ret);
        return nullptr;
    }

    //Do Some Stuff Here
    LOGI("Create ZPL Image");
    bool success = ZPLconvertImage(&info, pixels,&retValue);

    //Unlock Pixels and return result
    AndroidBitmap_unlockPixels(JEnv, bitmap);
    jstring result =  success ? JEnv->NewStringUTF(retValue) : nullptr;
    if (retValue != nullptr) free(retValue);
    return result;
}
/***************************************************************************************/
jstring JNICALL JNI_createBitmapCPC(JNIEnv *JEnv, jclass JCls, jobject bitmap)
{
    AndroidBitmapInfo info;
    int ret;
    void *pixels;
    char* retValue;

    //Get Bitmap Info
    if ((ret = AndroidBitmap_getInfo(JEnv, bitmap, &info)) < 0)
    {
        LOGE("AndroidBitmap_getInfo() failed ! error=%d", ret);
        return nullptr;
    }

    if ((ret = AndroidBitmap_lockPixels(JEnv, bitmap, &pixels)) < 0)
    {
        LOGE("AndroidBitmap_lockPixels() failed ! error=%d", ret);
        return nullptr;
    }

    //Do Some Stuff Here
    LOGI("Create CPCL Image");
    bool success = CPCconvertImage(&info, pixels,&retValue);

    //Unlock Pixels and return result
    AndroidBitmap_unlockPixels(JEnv, bitmap);
    jstring result =  success ? JEnv->NewStringUTF(retValue) : nullptr;
    if (retValue != nullptr) free(retValue);
    return result;
}