// SPDX-License-Identifier: CC0-1.0
package org.getflourish.beeswax

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.EditText

// The lock code field. Since Android 14, autofill services can act on fields marked not important for
// autofill, so password managers offered to save and fill the code. A field whose autofill type is none
// is never autofilled or saved.
class CodeField(context: Context, attrs: AttributeSet? = null) : EditText(context, attrs) {
    override fun getAutofillType() = View.AUTOFILL_TYPE_NONE
}
