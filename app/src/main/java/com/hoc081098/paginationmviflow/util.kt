package com.hoc081098.paginationmviflow

import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.fragment.app.Fragment

val Context.isOrientationPortrait get() = this.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

fun Context.toast(text: CharSequence) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

fun Fragment.toast(text: CharSequence) = requireContext().toast(text)