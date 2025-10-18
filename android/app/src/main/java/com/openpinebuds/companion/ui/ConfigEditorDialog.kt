package com.openpinebuds.companion.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.openpinebuds.companion.R
import com.openpinebuds.companion.data.ButtonAction
import com.openpinebuds.companion.data.GestureType
import com.openpinebuds.companion.databinding.DialogConfigEditorBinding

/**
 * Dialog for editing button action configuration
 */
class ConfigEditorDialog : DialogFragment() {

    private var _binding: DialogConfigEditorBinding? = null
    private val binding get() = _binding!!

    private var isLeftEarbud: Boolean = true
    private var gestureType: GestureType = GestureType.SINGLE_TAP
    private var currentAction: ButtonAction = ButtonAction.PLAY_PAUSE
    private var onActionSelected: ((GestureType, ButtonAction) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogConfigEditorBinding.inflate(LayoutInflater.from(context))

        // Get arguments
        isLeftEarbud = arguments?.getBoolean(ARG_IS_LEFT) ?: true
        gestureType = GestureType.valueOf(arguments?.getString(ARG_GESTURE) ?: GestureType.SINGLE_TAP.name)
        currentAction = ButtonAction.fromCode(arguments?.getInt(ARG_ACTION) ?: ButtonAction.PLAY_PAUSE.code)

        setupUI()

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("${if (isLeftEarbud) "Left" else "Right"} Earbud - ${gestureType.displayName}")
            .setView(binding.root)
            .setPositiveButton("Save") { _, _ ->
                val selectedAction = getSelectedAction()
                onActionSelected?.invoke(gestureType, selectedAction)
            }
            .setNegativeButton("Cancel", null)
            .create()
    }

    private fun setupUI() {
        // Create radio buttons for each action
        ButtonAction.values().forEach { action ->
            val radioButton = RadioButton(context).apply {
                id = View.generateViewId()
                text = action.displayName
                tag = action
                isChecked = (action == currentAction)
                setPadding(16, 16, 16, 16)
            }
            binding.radioGroupActions.addView(radioButton)
        }
    }

    private fun getSelectedAction(): ButtonAction {
        val selectedId = binding.radioGroupActions.checkedRadioButtonId
        val selectedButton = binding.radioGroupActions.findViewById<RadioButton>(selectedId)
        return selectedButton?.tag as? ButtonAction ?: currentAction
    }

    fun setOnActionSelectedListener(listener: (GestureType, ButtonAction) -> Unit) {
        onActionSelected = listener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_IS_LEFT = "is_left"
        private const val ARG_GESTURE = "gesture"
        private const val ARG_ACTION = "action"

        fun newInstance(
            isLeftEarbud: Boolean,
            gestureType: GestureType,
            currentAction: ButtonAction,
            onActionSelected: (GestureType, ButtonAction) -> Unit
        ): ConfigEditorDialog {
            return ConfigEditorDialog().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_IS_LEFT, isLeftEarbud)
                    putString(ARG_GESTURE, gestureType.name)
                    putInt(ARG_ACTION, currentAction.code)
                }
                setOnActionSelectedListener(onActionSelected)
            }
        }
    }
}
