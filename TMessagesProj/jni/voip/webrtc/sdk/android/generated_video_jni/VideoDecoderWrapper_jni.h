// Copyright 2014 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.


// This file is autogenerated by
//     third_party/jni_zero/jni_generator.py
// For
//     org/webrtc/VideoDecoderWrapper

#ifndef org_webrtc_VideoDecoderWrapper_JNI
#define org_webrtc_VideoDecoderWrapper_JNI

#include <jni.h>

#include "third_party/jni_zero/jni_export.h"
#include "webrtc/sdk/android/src/jni/jni_generator_helper.h"


// Step 1: Forward declarations.

JNI_ZERO_COMPONENT_BUILD_EXPORT extern const char kClassPath_org_webrtc_VideoDecoderWrapper[];
const char kClassPath_org_webrtc_VideoDecoderWrapper[] = "org/webrtc/VideoDecoderWrapper";
// Leaking this jclass as we cannot use LazyInstance from some threads.
JNI_ZERO_COMPONENT_BUILD_EXPORT std::atomic<jclass> g_org_webrtc_VideoDecoderWrapper_clazz(nullptr);
#ifndef org_webrtc_VideoDecoderWrapper_clazz_defined
#define org_webrtc_VideoDecoderWrapper_clazz_defined
inline jclass org_webrtc_VideoDecoderWrapper_clazz(JNIEnv* env) {
  return jni_zero::LazyGetClass(env, kClassPath_org_webrtc_VideoDecoderWrapper,
      &g_org_webrtc_VideoDecoderWrapper_clazz);
}
#endif


// Step 2: Constants (optional).


// Step 3: Method stubs.
namespace webrtc {
namespace jni {

JNI_BOUNDARY_EXPORT void Java_org_webrtc_VideoDecoderWrapper_nativeOnDecodedFrame(
    JNIEnv* env,
    jclass jcaller,
    jlong nativeVideoDecoderWrapper,
    jobject frame,
    jobject decodeTimeMs,
    jobject qp) {
  VideoDecoderWrapper* native = reinterpret_cast<VideoDecoderWrapper*>(nativeVideoDecoderWrapper);
  CHECK_NATIVE_PTR(env, jcaller, native, "OnDecodedFrame");
  return native->OnDecodedFrame(env, jni_zero::JavaParamRef<jobject>(env, frame),
      jni_zero::JavaParamRef<jobject>(env, decodeTimeMs), jni_zero::JavaParamRef<jobject>(env, qp));
}


static std::atomic<jmethodID> g_org_webrtc_VideoDecoderWrapper_createDecoderCallback1(nullptr);
static jni_zero::ScopedJavaLocalRef<jobject> Java_VideoDecoderWrapper_createDecoderCallback(JNIEnv*
    env, jlong nativeDecoder) {
  jclass clazz = org_webrtc_VideoDecoderWrapper_clazz(env);
  CHECK_CLAZZ(env, clazz,
      org_webrtc_VideoDecoderWrapper_clazz(env), nullptr);

  jni_zero::JniJavaCallContextChecked call_context;
  call_context.Init<
      jni_zero::MethodID::TYPE_STATIC>(
          env,
          clazz,
          "createDecoderCallback",
          "(J)Lorg/webrtc/VideoDecoder$Callback;",
          &g_org_webrtc_VideoDecoderWrapper_createDecoderCallback1);

  jobject ret =
      env->CallStaticObjectMethod(clazz,
          call_context.base.method_id, nativeDecoder);
  return jni_zero::ScopedJavaLocalRef<jobject>(env, ret);
}

}  // namespace jni
}  // namespace webrtc

#endif  // org_webrtc_VideoDecoderWrapper_JNI
