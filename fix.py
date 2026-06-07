import re

with open('app/src/main/java/com/mobisec/omniip/ui/ToolkitScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Add imports
imports = '''
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.clickable
'''

# Insert imports after the first few imports
content = re.sub(r'(import androidx.compose.foundation.background\n)', r'\1' + imports, content, count=1)

# Remove the broken ToolkitScreen (everything after the last @Composable fun ToolkitScreen)
idx = content.rfind('@Composable\nfun ToolkitScreen')
if idx != -1:
    content = content[:idx]

# Append the new ToolkitScreen
new_toolkit_screen = '''
@Composable
fun ToolkitScreen(navController: androidx.navigation.NavController) {
    val tools = listOf(
        Triple("Lan Sweep", "lansweep", Icons.Default.Search),
        Triple("Ping", "ping", Icons.Default.Send),
        Triple("Whois", "whois", Icons.Default.Person),
        Triple("Traceroute", "traceroute", Icons.Default.LocationOn),
        Triple("DNS Lookup", "dns", Icons.Default.Search),
        Triple("Port Scanner", "portscan", Icons.Default.Lock),
        Triple("IP Calculator", "ipcalc", Icons.Default.Add),
        Triple("Connection Log", "connlog", Icons.Default.List),
        Triple("Router Setup", "router", Icons.Default.Home),
        Triple("IP Converter", "ipconv", Icons.Default.Refresh),
        Triple("WiFi Scanner", "wifi", Icons.Default.Search),
        Triple("Network Stats", "netstats", Icons.Default.Info)
    )

    Column(modifier = Modifier.fillMaxSize().background(PureBlack).padding(16.dp)) {
        Text("TACTICAL TOOLKIT", color = MatrixGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(tools) { (title, route, icon) ->
                Card(
                    modifier = Modifier.fillMaxWidth().height(100.dp).clickable { navController.navigate(route) },
                    colors = CardDefaults.cardColors(containerColor = com.mobisec.omniip.ui.theme.SurfaceLevel1)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(icon, contentDescription = title, tint = MatrixGreen, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(title, color = MatrixGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}
'''

content += new_toolkit_screen

with open('app/src/main/java/com/mobisec/omniip/ui/ToolkitScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("ToolkitScreen fixed.")
