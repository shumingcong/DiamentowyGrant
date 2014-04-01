LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

# Visual Odometry library
include ../OpenCV-2.4.8-android-sdk/sdk/native/jni/OpenCV.mk

LOCAL_MODULE    := VisualOdometryModule
LOCAL_SRC_FILES := VisualOdometryModuleExport.cpp
LOCAL_LDLIBS +=  -llog -ldl
LOCAL_STATIC_LIBRARIES := FivePointStaticLibrary NonFreeStaticLibrary ScaleEstimationStaticLibrary

include $(BUILD_SHARED_LIBRARY)