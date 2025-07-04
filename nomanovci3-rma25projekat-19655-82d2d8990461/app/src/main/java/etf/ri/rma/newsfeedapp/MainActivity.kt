package etf.ri.rma.newsfeedapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.NavGraph
import androidx.navigation.compose.rememberNavController
import etf.ri.rma.newsfeedapp.screen.MainNavGraph
import etf.ri.rma.newsfeedapp.ui.theme.NewsFeedAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NewsFeedAppTheme {
                val navController = rememberNavController()
                MainNavGraph(navController)
            }
        }
    }
}