package com.totogo.core.firebase

import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.totogo.core.repo.AuthRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Production phone OTP via Firebase Authentication. */
class FirebasePhoneAuthRepository : AuthRepository {
    override val isDemo = false
    private val auth = FirebaseAuth.getInstance()
    private var verificationId: String? = null
    private var autoCredential: PhoneAuthCredential? = null

    override suspend fun sendCode(activity: Activity, phone: String): Result<Unit> = suspendCancellableCoroutine { cont ->
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                autoCredential = credential
                if (cont.isActive) cont.resume(Result.success(Unit))
            }
            override fun onVerificationFailed(e: FirebaseException) {
                if (cont.isActive) cont.resume(Result.failure(e))
            }
            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                verificationId = id
                if (cont.isActive) cont.resume(Result.success(Unit))
            }
        }
        PhoneAuthProvider.verifyPhoneNumber(
            PhoneAuthOptions.newBuilder(auth).setPhoneNumber(phone).setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity).setCallbacks(callbacks).build()
        )
    }

    override suspend fun verify(phone: String, code: String): Result<String> = runCatching {
        val cred = autoCredential ?: PhoneAuthProvider.getCredential(verificationId ?: error("Request a code first"), code)
        val result = auth.signInWithCredential(cred).await()
        FirebaseConfig.registerToken()
        result.user?.uid ?: error("Sign-in failed")
    }
}
