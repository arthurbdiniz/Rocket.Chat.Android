package chat.rocket.android.authentication.server.ui

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.AdapterView
import android.widget.ArrayAdapter
import chat.rocket.android.BuildConfig
import chat.rocket.android.R
import chat.rocket.android.authentication.domain.model.LoginDeepLinkInfo
import chat.rocket.android.authentication.loginoptions.ui.LoginOptionsFragment
import chat.rocket.android.authentication.server.presentation.ServerPresenter
import chat.rocket.android.authentication.server.presentation.ServerView
import chat.rocket.android.authentication.ui.AuthenticationActivity
import chat.rocket.android.helper.KeyboardHelper
import chat.rocket.android.util.extensions.*
import chat.rocket.common.util.ifNull
import dagger.android.support.AndroidSupportInjection
import kotlinx.android.synthetic.main.fragment_authentication_server.*
import okhttp3.HttpUrl
import javax.inject.Inject

class ServerFragment : Fragment(), ServerView {
    @Inject
    lateinit var presenter: ServerPresenter
    private var deepLinkInfo: LoginDeepLinkInfo? = null
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        text_server_url.isCursorVisible = KeyboardHelper.isSoftKeyboardShown(constraint_layout.rootView)
    }

    companion object {
        private const val DEEP_LINK_INFO = "DeepLinkInfo"

        fun newInstance(deepLinkInfo: LoginDeepLinkInfo?) = ServerFragment().apply {
            arguments = Bundle().apply {
                putParcelable(DEEP_LINK_INFO, deepLinkInfo)
            }
        }
    }

    private var protocol = "https://"
    private var ignoreChange = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidSupportInjection.inject(this)

        deepLinkInfo = arguments?.getParcelable(DEEP_LINK_INFO)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        container?.inflate(R.layout.fragment_authentication_server)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        constraint_layout.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        setupOnClickListener()

        deepLinkInfo?.let {
            val uri = Uri.parse(it.url)
            uri?.let { text_server_url.hintContent = it.host }
            presenter.deepLink(it)
        }

        text_server_protocol.adapter = ArrayAdapter<String>(activity,
                R.layout.spinner_list, arrayOf("https://", "http://"))
        text_server_protocol.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when(position) {
                    0 -> {
                        protocol = "https://"
                    }
                    1 -> {
                        if (ignoreChange) {
                            protocol = "http://"
                        } else {
                            ui {
                                AlertDialog.Builder(it)
                                        .setTitle(R.string.msg_warning)
                                        .setMessage(R.string.msg_http_insecure)
                                        .setPositiveButton(R.string.msg_proceed) { _, _ ->
                                            protocol = "http://"
                                        }
                                        .setNegativeButton(R.string.msg_cancel) { _, _ ->
                                            text_server_protocol.setSelection(0)
                                        }
                                        .setCancelable(false)
                                        .create()
                                        .show()
                            }
                        }
                    }
                }
                ignoreChange = false
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        constraint_layout.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
    }

    override fun showInvalidServerUrlMessage() = showMessage(getString(R.string.msg_invalid_server_url))

    override fun showLoading() {
        ui {
            enableUserInput(false)
            view_loading.setVisible(true)
        }
    }

    override fun hideLoading() {
        ui {
            view_loading.setVisible(false)
            enableUserInput(true)
        }
    }

    override fun showMessage(resId: Int) {
        ui {
            showToast(resId)
        }
    }

    override fun showMessage(message: String) {
        ui {
            showToast(message)
        }
    }

    override fun showGenericErrorMessage() {
        showMessage(getString(R.string.msg_generic_error))
    }

    override fun alertNotRecommendedVersion() {
        ui {
            hideLoading()
            AlertDialog.Builder(it)
                .setMessage(getString(R.string.msg_ver_not_recommended, BuildConfig.RECOMMENDED_SERVER_VERSION))
                .setPositiveButton(R.string.msg_ok, { _, _ ->
                    performConnect()
                })
                .create()
                .show()
        }
    }

    override fun blockAndAlertNotRequiredVersion() {
        ui {
            hideLoading()
            AlertDialog.Builder(it)
                .setMessage(getString(R.string.msg_ver_not_minimum, BuildConfig.REQUIRED_SERVER_VERSION))
                .setPositiveButton(R.string.msg_ok, null)
                .setOnDismissListener {
                    // reset the deeplink info, so the user can log to another server...
                    deepLinkInfo = null
                }
                .create()
                .show()
        }
    }

    override fun versionOk() {
        performConnect()
    }

    override fun errorCheckingServerVersion() {
        hideLoading()
        showMessage(R.string.msg_error_checking_server_version)
    }

    override fun errorInvalidProtocol() {
        hideLoading()
        showMessage(R.string.msg_invalid_server_protocol)
    }

    override fun updateServerUrl(url: HttpUrl) {
        if (activity != null && view != null) {
            if (url.scheme() == "https") text_server_protocol.setSelection(0) else text_server_protocol.setSelection(1)
            protocol = "${url.scheme()}://"

            val serverUrl = url.toString().removePrefix("${url.scheme()}://")
            text_server_url.textContent = serverUrl
        }
    }

    private fun performConnect() {
        ui {
            deepLinkInfo?.let {
                presenter.deepLink(it)
            }.ifNull {
                val url = text_server_url.textContent.ifEmpty(text_server_url.hintContent)
                presenter.connect("$protocol${url.sanitize()}")
            }
        }
    }

    private fun enableUserInput(value: Boolean) {
        button_connect.isEnabled = value
        text_server_url.isEnabled = value
    }

    private fun setupOnClickListener() {
        ui {
            button_connect.setOnClickListener {
//                val url = text_server_url.textContent.ifEmpty(text_server_url.hintContent)
//                presenter.checkServer("${protocol}${url.sanitize()}")
                (activity as AuthenticationActivity).addFragmentBackStack("LoginOption", R.id.fragment_container){
                    LoginOptionsFragment.newInstance()
                }
            }
        }
    }
}