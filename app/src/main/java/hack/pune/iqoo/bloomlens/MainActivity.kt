package hack.pune.iqoo.bloomlens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import hack.pune.iqoo.bloomlens.state.MainViewModel
import hack.pune.iqoo.bloomlens.ui.BloomLensRoot
import hack.pune.iqoo.bloomlens.ui.theme.BloomLensTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        (application as BloomLensApp).container.viewModelFactory
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BloomLensTheme {
                BloomLensRoot(viewModel)
            }
        }
    }
}