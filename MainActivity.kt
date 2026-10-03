@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.yourapp.budgetapp

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Calculate
import com.yourapp.budgetapp.ui.theme.BudgetAppTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: BudgetViewModel = viewModel(
                factory = run {
                    val application = LocalContext.current.applicationContext as Application
                    object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return BudgetViewModel(application) as T
                        }
                    }
                }
            )
            val isDarkMode by viewModel.darkMode.collectAsState()
            val isLocked by viewModel.isLocked.collectAsState()
            
            BudgetAppTheme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isLocked) {
                        AuthScreen { pin -> viewModel.unlockApp(pin) }
                    } else {
                        BudgetApp(viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun AuthScreen(onUnlock: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(24.dp))
        Text("App Locked", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Please enter your PIN to continue", color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it },
            label = { Text("PIN") },
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
            isError = error
        )
        
        if (error) {
            Text("Invalid PIN", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = {
                if (!onUnlock(pin)) {
                    error = true
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Unlock")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetApp(
    viewModel: BudgetViewModel
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Notification Permission Launcher
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (!isGranted) {
                Toast.makeText(context, "Notifications are disabled. You won't receive alerts or reminders.", Toast.LENGTH_LONG).show()
            }
        }
    )

    var showAddTransaction by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<Transaction?>(null) }
    var showEditDialog by remember { mutableStateOf<Transaction?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showBudgetGoalDialog by remember { mutableStateOf(false) }
    var showSavingsCalculator by remember { mutableStateOf(false) }
    var showAddBillReminder by remember { mutableStateOf(false) }
    var showAddSavingsGoal by remember { mutableStateOf(false) }
    var showContributionDialog by remember { mutableStateOf<SavingsGoal?>(null) }
    
    val transactions by viewModel.transactions.collectAsState()
    val filteredTransactions by viewModel.filteredTransactions.collectAsState()
    val billReminders by viewModel.billReminders.collectAsState()
    val monthlySummary by viewModel.monthlySummary.collectAsState()
    val dailyRemaining by viewModel.dailyRemaining.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val budgetGoals by viewModel.budgetGoals.collectAsState()
    val savingsGoals by viewModel.savingsGoals.collectAsState()
    val categorySpending by viewModel.categorySpending.collectAsState()
    val spendingTrends by viewModel.spendingTrends.collectAsState()
    val topSpendingCategories by viewModel.topSpendingCategories.collectAsState()
    val spendingForecast by viewModel.spendingForecast.collectAsState()
    val currentBalance by viewModel.currentBalance.collectAsState()
    val totalSavings by viewModel.totalSavings.collectAsState()
    val healthScore by viewModel.financialHealthScore.collectAsState()
    val hideBalances by viewModel.hideBalances.collectAsState()
    val privacyMode by viewModel.privacyMode.collectAsState()
    val dbCategories by viewModel.categories.collectAsState()
    val financialInsights by viewModel.financialInsights.collectAsState()
    
    val dailyExpenses by viewModel.dailyExpenses.collectAsState()
    val incomeCalendar by viewModel.incomeCalendar.collectAsState()
    val billsCalendar by viewModel.billsCalendar.collectAsState()
    val budgetCalendar by viewModel.budgetCalendar.collectAsState()
    val savingsMilestones by viewModel.savingsMilestones.collectAsState()
    val upcomingReminders by viewModel.upcomingReminders.collectAsState()

    var currentScreen by remember { mutableStateOf("Home") }
    var showManageCategories by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        viewModel.loadCurrentMonthTransactions()
    }
    
    Scaffold(
        floatingActionButton = {
            if (currentScreen == "Home") {
                FloatingActionButton(
                    onClick = { showAddTransaction = true },
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Transaction")
                }
            }
        },
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = when(currentScreen) {
                            "Home" -> "Budget Tracker"
                            "Calendar" -> "Financial Calendar"
                            "Insights" -> "Data Analysis"
                            else -> "Budget App"
                        }
                    ) 
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentScreen == "Home",
                    onClick = { currentScreen = "Home" },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = currentScreen == "Calendar",
                    onClick = { currentScreen = "Calendar" },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = "Calendar") },
                    label = { Text("Calendar") }
                )
                NavigationBarItem(
                    selected = currentScreen == "Insights",
                    onClick = { currentScreen = "Insights" },
                    icon = { Icon(Icons.Default.Analytics, contentDescription = "Insights") },
                    label = { Text("Insights") }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                when (currentScreen) {
                    "Home" -> {
                        MainScreen(
                            transactions = filteredTransactions,
                            summary = monthlySummary,
                            dailyRemaining = dailyRemaining,
                            selectedMonth = selectedMonth,
                            searchQuery = searchQuery,
                            selectedCategory = selectedCategory,
                            categories = dbCategories,
                            budgetGoals = budgetGoals,
                            savingsGoals = savingsGoals,
                            categorySpending = categorySpending,
                            currentBalance = currentBalance,
                            totalSavings = totalSavings,
                            healthScore = healthScore,
                            hideBalances = hideBalances,
                            privacyMode = privacyMode,
                            onTransactionLongClick = { transaction ->
                                showDeleteDialog = transaction
                            },
                            onTransactionEdit = { transaction ->
                                showEditDialog = transaction
                            },
                            onSearchQueryChange = { viewModel.setSearchQuery(it) },
                            onCategorySelected = { viewModel.setCategoryFilter(it) },
                            onMonthNavigate = { direction -> viewModel.navigateMonth(direction) },
                            onAddBudgetGoal = { showBudgetGoalDialog = true },
                            onSmartEntry = { viewModel.parseNaturalLanguageEntry(it) },
                            billReminders = billReminders,
                            onAddBillReminder = { showAddBillReminder = true },
                            onPayBill = { viewModel.payBill(it) },
                            onAddSavingsGoal = { showAddSavingsGoal = true },
                            onContributeSavings = { showContributionDialog = it },
                            onPauseRecurring = { viewModel.pauseRecurring(it) },
                            onResumeRecurring = { viewModel.resumeRecurring(it) },
                            onSkipOccurrence = { viewModel.skipOccurrence(it) }
                        )
                    }
                    "Calendar" -> {
                        CalendarScreen(
                            selectedMonth = selectedMonth,
                            dailyExpenses = dailyExpenses,
                            incomeCalendar = incomeCalendar,
                            billsCalendar = billsCalendar,
                            budgetCalendar = budgetCalendar,
                            savingsMilestones = savingsMilestones,
                            upcomingReminders = upcomingReminders,
                            privacyMode = privacyMode,
                            onMonthNavigate = { direction -> viewModel.navigateMonth(direction) }
                        )
                    }
                    "Insights" -> {
                        InsightsScreen(
                            trends = spendingTrends,
                            topCategories = topSpendingCategories,
                            forecast = spendingForecast,
                            budgetGoals = budgetGoals,
                            categorySpending = categorySpending,
                            financialInsights = financialInsights,
                            onCalculateSavings = { showSavingsCalculator = true }
                        )
                    }
                }
            }
            
            // Error/Success Snackbar
            if (errorMessage != null) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { viewModel.clearError() }
                        ) {
                            Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
            
            if (successMessage != null) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = successMessage ?: "",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { viewModel.clearSuccess() }
                        ) {
                            Text("OK", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
        }
    }
    
    // Add Transaction Dialog
    if (showAddTransaction) {
        AddTransactionDialog(
            categories = dbCategories,
            onSave = { amount, category, merchant, note, tags, type, isRecurring, frequency, endDate ->
                viewModel.addTransaction(amount, category, merchant, note, tags, type, isRecurring, frequency, endDate)
                showAddTransaction = false
            },
            onCancel = { showAddTransaction = false }
        )
    }
    
    // Edit Transaction Dialog
    if (showEditDialog != null) {
        EditTransactionDialog(
            transaction = showEditDialog!!,
            categories = dbCategories,
            onSave = { transaction ->
                viewModel.updateTransaction(transaction)
                showEditDialog = null
            },
            onCancel = { showEditDialog = null }
        )
    }
    
    // Delete Confirmation Dialog
    if (showDeleteDialog != null) {
        DeleteConfirmationDialog(
            transaction = showDeleteDialog!!,
            onConfirm = {
                viewModel.deleteTransaction(showDeleteDialog!!)
                showDeleteDialog = null
            },
            onCancel = { showDeleteDialog = null }
        )
    }
    
    // Budget Goal Dialog
    if (showBudgetGoalDialog) {
        AddBudgetGoalDialog(
            onSave = { category, amount, period ->
                viewModel.addBudgetGoal(category, amount, period)
                showBudgetGoalDialog = false
            },
            onCancel = { showBudgetGoalDialog = false }
        )
    }

    // Add Bill Reminder Dialog
    if (showAddBillReminder) {
        AddBillReminderDialog(
            onSave = { title, amount, date, category ->
                viewModel.addBillReminder(title, amount, date, category)
                showAddBillReminder = false
            },
            onCancel = { showAddBillReminder = false }
        )
    }

    // Add Savings Goal Dialog
    if (showAddSavingsGoal) {
        AddSavingsGoalDialog(
            onSave = { title, target, deadline, priority ->
                viewModel.addSavingsGoal(title, target, deadline, priority)
                showAddSavingsGoal = false
            },
            onCancel = { showAddSavingsGoal = false }
        )
    }

    // Savings Contribution Dialog
    if (showContributionDialog != null) {
        SavingsContributionDialog(
            goal = showContributionDialog!!,
            onConfirm = { amount ->
                viewModel.contributeToSavings(showContributionDialog!!, amount)
                showContributionDialog = null
            },
            onCancel = { showContributionDialog = null }
        )
    }

    // Savings Calculator Dialog
    if (showSavingsCalculator) {
        SavingsCalculatorDialog(
            onCalculate = { amt: Double, dt: Long ->
                viewModel.calculateSavingsGoal(amt, dt)
            },
            onDismiss = { showSavingsCalculator = false }
        )
    }
    
    // Settings Screen
    if (showSettings) {
        SettingsScreen(
            viewModel = viewModel,
            onDismiss = { showSettings = false },
            onExportClick = {
                scope.launch {
                    try {
                        val csvData = viewModel.exportToCSV()
                        val file = File(context.filesDir, "budget_data_${System.currentTimeMillis()}.csv")
                        file.writeText(csvData)
                        Toast.makeText(context, "Exported to ${file.absolutePath}", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onClearData = {
                viewModel.clearAllTransactions()
                showSettings = false
            },
            onManageCategories = {
                showManageCategories = true
            }
        )
    }

    if (showManageCategories) {
        ManageCategoriesDialog(
            categories = dbCategories,
            onAdd = { name, type -> viewModel.addCategory(name, type) },
            onDelete = { viewModel.deleteCategory(it) },
            onDismiss = { showManageCategories = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    transactions: List<Transaction>,
    summary: MonthlySummary,
    dailyRemaining: String,
    selectedMonth: Calendar,
    searchQuery: String,
    selectedCategory: String?,
    categories: List<Category>,
    budgetGoals: List<BudgetGoal>,
    savingsGoals: List<SavingsGoal>,
    categorySpending: Map<String, Double>,
    currentBalance: Double,
    totalSavings: Double,
    healthScore: Int,
    hideBalances: Boolean,
    privacyMode: Boolean,
    onTransactionLongClick: (Transaction) -> Unit,
    onTransactionEdit: (Transaction) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onMonthNavigate: (Int) -> Unit,
    onAddBudgetGoal: () -> Unit,
    onSmartEntry: (String) -> Unit,
    billReminders: List<BillReminder>,
    onAddBillReminder: () -> Unit,
    onPayBill: (BillReminder) -> Unit,
    onAddSavingsGoal: () -> Unit,
    onContributeSavings: (SavingsGoal) -> Unit,
    onPauseRecurring: (Transaction) -> Unit,
    onResumeRecurring: (Transaction) -> Unit,
    onSkipOccurrence: (Transaction) -> Unit
) {
    val categoryNames = categories.map { it.name }.distinct()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Dashboard Header / Balance Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Current Balance", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (hideBalances) "****" else "KSh ${formatCurrency(currentBalance)}",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Total Savings", style = MaterialTheme.typography.labelMedium)
                            Text(
                                if (hideBalances) "****" else "KSh ${formatCurrency(totalSavings)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Health Score", style = MaterialTheme.typography.labelMedium)
                            Text(
                                "$healthScore/100",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    healthScore > 80 -> Color(0xFF4CAF50)
                                    healthScore > 50 -> Color(0xFFFFC107)
                                    else -> Color(0xFFF44336)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Quick Add Transaction
        item {
            Column {
                Text("Quick Log", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf(
                        "Food" to Icons.Default.Fastfood,
                        "Transport" to Icons.Default.DirectionsCar,
                        "Salary" to Icons.Default.Payments
                    ).forEach { (cat, icon) ->
                        OutlinedButton(
                            onClick = { 
                                // Ideally this would open the Add dialog with the category pre-filled
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(8.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(icon, cat, modifier = Modifier.size(24.dp))
                                Text(cat, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        // Quick Summary Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Income", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "KSh ${formatCurrency(summary.totalIncome)}",
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Expenses", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "KSh ${formatCurrency(summary.totalExpenses)}",
                            color = Color(0xFFF44336),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Month Navigation & Monthly Overview
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Monthly Overview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onMonthNavigate(-1) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Prev", modifier = Modifier.size(20.dp))
                        }
                        Text(
                            SimpleDateFormat("MMM yyyy", Locale.US).format(selectedMonth.time),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        IconButton(onClick = { onMonthNavigate(1) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next", modifier = Modifier.size(20.dp))
                        }
                    }
                }
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Budget Remaining", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "KSh ${formatCurrency(summary.remaining)}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (summary.remaining >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = {
                                val total = summary.totalIncome.takeIf { it > 0 } ?: (summary.totalExpenses + summary.remaining).coerceAtLeast(1.0)
                                (summary.totalExpenses / total).toFloat().coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = if (summary.remaining >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeCap = StrokeCap.Round
                        )
                        Text(
                            text = dailyRemaining,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Search and Filter
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                var smartEntry by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = smartEntry,
                    onValueChange = { smartEntry = it },
                    placeholder = { Text("Smart entry: '500 for lunch'") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { 
                            if (smartEntry.isNotBlank()) {
                                onSmartEntry(smartEntry)
                                smartEntry = ""
                            }
                        }) {
                            Icon(Icons.Default.Send, "Add Smartly")
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search transactions...") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, "Search") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { onCategorySelected(null) },
                        label = { Text("All") }
                    )
                    categoryNames.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { onCategorySelected(category) },
                            label = { Text(category) }
                        )
                    }
                }
            }
        }

        // Upcoming Bills Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Upcoming Bills",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = onAddBillReminder) {
                            Text("+ Add Bill")
                        }
                    }
                    
                    val unpaid = billReminders.filter { !it.isPaid }
                    if (unpaid.isEmpty()) {
                        Text("No pending bills", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        unpaid.forEach { reminder ->
                            BillReminderItem(reminder, onPay = { onPayBill(reminder) })
                        }
                    }
                }
            }
        }

        // Savings Goals Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Savings Goals",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = onAddSavingsGoal) {
                            Text("+ Add Goal")
                        }
                    }
                    
                    if (savingsGoals.isEmpty()) {
                        Text("No active savings goals", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        savingsGoals.filter { !it.isCompleted }.forEach { goal ->
                            SavingsGoalItem(goal, onContribute = { onContributeSavings(goal) })
                        }
                    }
                }
            }
        }

        // Budget Goals Section
        if (budgetGoals.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Budget Goals",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = onAddBudgetGoal) {
                                Text("+ Add Goal")
                            }
                        }
                        budgetGoals.forEach { goal ->
                            val spent = categorySpending[goal.category] ?: 0.0
                            val percentage = if (goal.amount > 0) ((spent / goal.amount) * 100).toFloat() else 0f
                            BudgetGoalItem(
                                category = goal.category,
                                spent = spent,
                                budget = goal.amount,
                                percentage = percentage
                            )
                        }
                    }
                }
            }
        }

        // Recent Transactions Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Transactions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${transactions.size} entries",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Transaction List
        if (transactions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No transactions found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(transactions.take(10)) { transaction ->
                TransactionItem(
                    transaction = transaction,
                    privacyMode = privacyMode,
                    onLongClick = { onTransactionLongClick(transaction) },
                    onEdit = { onTransactionEdit(transaction) },
                    onPause = { onPauseRecurring(transaction) },
                    onResume = { onResumeRecurring(transaction) },
                    onSkip = { onSkipOccurrence(transaction) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionItem(
    transaction: Transaction,
    privacyMode: Boolean,
    onLongClick: () -> Unit,
    onEdit: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkip: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .combinedClickable(
                onClick = onEdit,
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = transaction.category,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    )
                    Surface(
                        color = if (transaction.type == "INCOME") 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = transaction.type,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = if (transaction.type == "INCOME") 
                                MaterialTheme.colorScheme.onPrimaryContainer 
                            else 
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    if (transaction.isRecurring) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = "Recurring",
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                if (transaction.merchant.isNotEmpty()) {
                    Text(
                        text = "Merchant: ${transaction.merchant}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (transaction.note.isNotEmpty()) {
                    Text(
                        text = transaction.note,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (transaction.tags.isNotEmpty()) {
                    Text(
                        text = "Tags: ${transaction.tags}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = formatDate(transaction.date),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (transaction.isRecurring) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = { 
                                if (transaction.isRecurringPaused) onResume()
                                else onPause()
                            },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text(if (transaction.isRecurringPaused) "Resume" else "Pause", fontSize = 10.sp)
                        }
                        TextButton(
                            onClick = onSkip,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text("Skip Next", fontSize = 10.sp)
                        }
                    }
                }
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (privacyMode) "****" else "${if (transaction.type == "EXPENSE") "-" else "+"}KSh ${formatCurrency(transaction.amount)}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (transaction.type == "INCOME") 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.error
                )
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// NEW: Budget Goal Item
@Composable
fun BudgetGoalItem(
    category: String,
    spent: Double,
    budget: Double,
    percentage: Float
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = category, fontWeight = FontWeight.Medium)
            Text(
                text = "KSh ${formatCurrency(spent)} / KSh ${formatCurrency(budget)}",
                color = if (percentage > 100) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
        LinearProgressIndicator(
            progress = { (percentage / 100).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            color = when {
                percentage > 100 -> MaterialTheme.colorScheme.error
                percentage > 80 -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
            },
            strokeCap = StrokeCap.Round
        )
        Text(
            text = "${String.format(Locale.US, "%.1f", percentage)}% used",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// NEW: Add Budget Goal Dialog
@Composable
fun AddBudgetGoalDialog(
    onSave: (category: String, amount: Double, period: String) -> Unit,
    onCancel: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf("Food") }
    var amount by remember { mutableStateOf("") }
    var selectedPeriod by remember { mutableStateOf("MONTH") }
    var expanded by remember { mutableStateOf(false) }
    var periodExpanded by remember { mutableStateOf(false) }
    
    val categories = listOf(
        "Food", "Transport", "Rent", 
        "Shopping", "Entertainment", "Utilities", "Other"
    )
    val periods = listOf("WEEK", "MONTH", "YEAR", "CUSTOM")

    Dialog(
        onDismissRequest = onCancel
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Set Budget Goal",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Category Dropdown
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        trailingIcon = { 
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) 
                        }
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category) },
                                onClick = {
                                    selectedCategory = category
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Period Dropdown
                ExposedDropdownMenuBox(
                    expanded = periodExpanded,
                    onExpandedChange = { periodExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedPeriod,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Period") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        trailingIcon = { 
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = periodExpanded) 
                        }
                    )
                    ExposedDropdownMenu(
                        expanded = periodExpanded,
                        onDismissRequest = { periodExpanded = false }
                    ) {
                        periods.forEach { period ->
                            DropdownMenuItem(
                                text = { Text(period) },
                                onClick = {
                                    selectedPeriod = period
                                    periodExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Budget Amount") },
                    placeholder = { Text("0.00") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Text("KSh") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = {
                            val amountValue = amount.toDoubleOrNull()
                            if (amountValue != null && amountValue > 0) {
                                onSave(selectedCategory, amountValue, selectedPeriod)
                            }
                        },
                        modifier = Modifier.weight(2f),
                        enabled = amount.toDoubleOrNull() != null && amount.toDoubleOrNull()!! > 0
                    ) {
                        Text("Save Goal")
                    }
                }
            }
        }
    }
}

// NEW: Edit Transaction Dialog
@Composable
fun EditTransactionDialog(
    transaction: Transaction,
    categories: List<Category>,
    onSave: (Transaction) -> Unit,
    onCancel: () -> Unit
) {
    var amount by remember { mutableStateOf(transaction.amount.toString()) }
    var selectedType by remember { mutableStateOf(transaction.type) }
    var selectedCategory by remember { mutableStateOf(transaction.category) }
    var merchant by remember { mutableStateOf(transaction.merchant) }
    var note by remember { mutableStateOf(transaction.note) }
    var tags by remember { mutableStateOf(transaction.tags) }
    var isRecurring by remember { mutableStateOf(transaction.isRecurring) }
    var selectedFrequency by remember { mutableStateOf(transaction.recurringFrequency) }
    var recurringEndDate by remember { mutableStateOf(transaction.recurringEndDate) }
    var isRecurringPaused by remember { mutableStateOf(transaction.isRecurringPaused) }
    
    var expanded by remember { mutableStateOf(false) }
    var freqExpanded by remember { mutableStateOf(false) }
    
    val incomeCategories = categories.filter { it.type == "INCOME" }.map { it.name }
    val expenseCategories = categories.filter { it.type == "EXPENSE" }.map { it.name }
    
    val frequencies = listOf("DAILY", "WEEKLY", "BIWEEKLY", "MONTHLY", "QUARTERLY", "YEARLY")

    // Ensure category is valid for type when type changes
    LaunchedEffect(selectedType) {
        val currentCategories = if (selectedType == "INCOME") incomeCategories else expenseCategories
        if (selectedCategory !in currentCategories) {
            selectedCategory = currentCategories.firstOrNull() ?: "Other"
        }
    }

    Dialog(
        onDismissRequest = onCancel
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .heightIn(max = 600.dp)
            ) {
                item {
                    Text(
                        text = "Edit Transaction",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedType == "EXPENSE",
                            onClick = { selectedType = "EXPENSE" },
                            label = { Text("Expense") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedType == "INCOME",
                            onClick = { selectedType = "INCOME" },
                            label = { Text("Income") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Amount") },
                        placeholder = { Text("0.00") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Text("KSh") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedCategory,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            trailingIcon = { 
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) 
                            }
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            val currentCategories = if (selectedType == "INCOME") incomeCategories else expenseCategories
                            currentCategories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category) },
                                    onClick = {
                                        selectedCategory = category
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = merchant,
                        onValueChange = { merchant = it },
                        label = { Text("Merchant") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Note (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = tags,
                        onValueChange = { tags = it },
                        label = { Text("Tags (comma separated)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Recurring Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Recurring Transaction")
                        Switch(
                            checked = isRecurring,
                            onCheckedChange = { isRecurring = it }
                        )
                    }

                    if (isRecurring) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Paused")
                            Switch(
                                checked = isRecurringPaused,
                                onCheckedChange = { isRecurringPaused = it }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        ExposedDropdownMenuBox(
                            expanded = freqExpanded,
                            onExpandedChange = { freqExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedFrequency,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Frequency") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                trailingIcon = { 
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = freqExpanded) 
                                }
                            )
                            ExposedDropdownMenu(
                                expanded = freqExpanded,
                                onDismissRequest = { freqExpanded = false }
                            ) {
                                frequencies.forEach { frequency ->
                                    DropdownMenuItem(
                                        text = { Text(frequency) },
                                        onClick = {
                                            selectedFrequency = frequency
                                            freqExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = if (recurringEndDate != null) formatDateShort(recurringEndDate!!) else "Never ends",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("End Date") },
                            modifier = Modifier.fillMaxWidth().clickable { 
                                // In real app, show date picker
                                recurringEndDate = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
                            },
                            trailingIcon = {
                                if (recurringEndDate != null) {
                                    IconButton(onClick = { recurringEndDate = null }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear")
                                    }
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        
                        Button(
                            onClick = {
                                val amountValue = amount.toDoubleOrNull()
                                if (amountValue != null && amountValue > 0) {
                                    val updatedTransaction = transaction.copy(
                                        amount = amountValue,
                                        category = selectedCategory,
                                        merchant = merchant,
                                        note = note,
                                        tags = tags,
                                        type = selectedType,
                                        isRecurring = isRecurring,
                                        recurringFrequency = if (isRecurring) selectedFrequency else "",
                                        recurringEndDate = if (isRecurring) recurringEndDate else null,
                                        isRecurringPaused = if (isRecurring) isRecurringPaused else false
                                    )
                                    onSave(updatedTransaction)
                                }
                            },
                            modifier = Modifier.weight(2f),
                            enabled = amount.toDoubleOrNull() != null && amount.toDoubleOrNull()!! > 0
                        ) {
                            Text("Update")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddTransactionDialog(
    categories: List<Category>,
    onSave: (amount: Double, category: String, merchant: String, note: String, tags: String, type: String, isRecurring: Boolean, frequency: String, endDate: Long?) -> Unit,
    onCancel: () -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("EXPENSE") }
    var selectedCategory by remember { mutableStateOf("Food") }
    var merchant by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var isRecurring by remember { mutableStateOf(false) }
    var selectedFrequency by remember { mutableStateOf("MONTHLY") }
    var recurringEndDate by remember { mutableStateOf<Long?>(null) }
    
    var expanded by remember { mutableStateOf(false) }
    var freqExpanded by remember { mutableStateOf(false) }
    
    val incomeCategories = categories.filter { it.type == "INCOME" }.map { it.name }
    val expenseCategories = categories.filter { it.type == "EXPENSE" }.map { it.name }
    
    val frequencies = listOf("DAILY", "WEEKLY", "BIWEEKLY", "MONTHLY", "QUARTERLY", "YEARLY")

    // Ensure category is valid for type when type changes
    LaunchedEffect(selectedType) {
        val currentCategories = if (selectedType == "INCOME") incomeCategories else expenseCategories
        if (selectedCategory !in currentCategories) {
            selectedCategory = currentCategories.firstOrNull() ?: "Other"
        }
    }

    Dialog(
        onDismissRequest = onCancel
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .heightIn(max = 600.dp)
            ) {
                item {
                    Text(
                        text = "Add Transaction",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedType == "EXPENSE",
                            onClick = { selectedType = "EXPENSE" },
                            label = { Text("Expense") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedType == "INCOME",
                            onClick = { selectedType = "INCOME" },
                            label = { Text("Income") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Amount") },
                        placeholder = { Text("0.00") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Text("KSh") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedCategory,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            trailingIcon = { 
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) 
                            }
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            val currentCategories = if (selectedType == "INCOME") incomeCategories else expenseCategories
                            currentCategories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category) },
                                    onClick = {
                                        selectedCategory = category
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = merchant,
                        onValueChange = { merchant = it },
                        label = { Text("Merchant") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Note (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = tags,
                        onValueChange = { tags = it },
                        label = { Text("Tags (comma separated)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Recurring Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Recurring Transaction")
                        Switch(
                            checked = isRecurring,
                            onCheckedChange = { isRecurring = it }
                        )
                    }

                    if (isRecurring) {
                        Spacer(modifier = Modifier.height(8.dp))
                        ExposedDropdownMenuBox(
                            expanded = freqExpanded,
                            onExpandedChange = { freqExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedFrequency,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Frequency") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                trailingIcon = { 
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = freqExpanded) 
                                }
                            )
                            ExposedDropdownMenu(
                                expanded = freqExpanded,
                                onDismissRequest = { freqExpanded = false }
                            ) {
                                frequencies.forEach { frequency ->
                                    DropdownMenuItem(
                                        text = { Text(frequency) },
                                        onClick = {
                                            selectedFrequency = frequency
                                            freqExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = if (recurringEndDate != null) formatDateShort(recurringEndDate!!) else "Never ends",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("End Date") },
                            modifier = Modifier.fillMaxWidth().clickable { 
                                // In real app, show date picker
                                recurringEndDate = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
                            },
                            trailingIcon = {
                                if (recurringEndDate != null) {
                                    IconButton(onClick = { recurringEndDate = null }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear")
                                    }
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        
                        Button(
                            onClick = {
                                val amountValue = amount.toDoubleOrNull()
                                if (amountValue != null && amountValue > 0) {
                                    onSave(amountValue, selectedCategory, merchant, note, tags, selectedType, isRecurring, selectedFrequency, recurringEndDate)
                                }
                            },
                            modifier = Modifier.weight(2f),
                            enabled = amount.toDoubleOrNull() != null && amount.toDoubleOrNull()!! > 0
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

// NEW: Insights Screen
@Composable
fun InsightsScreen(
    trends: SpendingTrend?,
    topCategories: List<CategoryInsight>,
    forecast: Double,
    budgetGoals: List<BudgetGoal>,
    categorySpending: Map<String, Double>,
    financialInsights: List<FinancialInsight>,
    onCalculateSavings: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Data Analysis & Insights",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Spending Forecast Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Monthly Spending Forecast", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "KSh ${formatCurrency(forecast)}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Based on your historical spending average.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // NEW: Automated Financial Insights Section
        if (financialInsights.isNotEmpty()) {
            item {
                Text(
                    text = "Automated Insights",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            items(financialInsights) { insight ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when (insight.type) {
                            "HIGH" -> MaterialTheme.colorScheme.errorContainer
                            "MEDIUM" -> MaterialTheme.colorScheme.tertiaryContainer
                            "POSITIVE" -> Color(0xFFE8F5E9)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (insight.type) {
                                "HIGH" -> Icons.Default.Warning
                                "MEDIUM" -> Icons.Default.Info
                                "POSITIVE" -> Icons.Default.CheckCircle
                                else -> Icons.Default.Lightbulb
                            },
                            contentDescription = null,
                            tint = when (insight.type) {
                                "HIGH" -> MaterialTheme.colorScheme.error
                                "MEDIUM" -> MaterialTheme.colorScheme.tertiary
                                "POSITIVE" -> Color(0xFF2E7D32)
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = insight.title,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = insight.description,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        // Spending Trends Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Spending Trends",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    if (trends != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("This Month", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("KSh ${formatCurrency(trends.currentMonthExpense)}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            
                            Icon(
                                imageVector = if (trends.percentChange > 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                contentDescription = null,
                                tint = if (trends.percentChange > 0) MaterialTheme.colorScheme.error else Color(0xFF4CAF50),
                                modifier = Modifier.size(32.dp)
                            )
                            
                            Column(horizontalAlignment = Alignment.End) {
                                Text("vs Last Month", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "${if (trends.percentChange > 0) "+" else ""}${String.format(Locale.US, "%.1f", trends.percentChange)}%",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (trends.percentChange > 0) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)
                                )
                            }
                        }
                    } else {
                        Text("Not enough data for trends yet.")
                    }
                }
            }
        }

        // Budget vs Actual Section
        item {
            Text(
                text = "Budget vs Actual",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (budgetGoals.isEmpty()) {
            item {
                Text(
                    text = "No budget goals set for this month.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(budgetGoals) { goal ->
                val spent = categorySpending[goal.category] ?: 0.0
                val percentage = if (goal.amount > 0) ((spent / goal.amount) * 100).toFloat() else 0f
                
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(goal.category, fontWeight = FontWeight.Medium)
                            Text("KSh ${formatCurrency(spent)} / ${formatCurrency(goal.amount)}")
                        }
                        LinearProgressIndicator(
                            progress = { (percentage / 100).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            color = if (percentage > 100) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            strokeCap = StrokeCap.Round
                        )
                        Text(
                            text = if (percentage > 100) 
                                "Over budget by KSh ${formatCurrency(spent - goal.amount)}" 
                            else 
                                "KSh ${formatCurrency(goal.amount - spent)} remaining",
                            fontSize = 12.sp,
                            color = if (percentage > 100) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)
                        )
                    }
                }
            }
        }

        // Spending Insights Section
        item {
            Text(
                text = "Spending Insights",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        items(topCategories.take(3)) { insight ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (insight.isHighest) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = insight.category.take(1),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(insight.category, fontWeight = FontWeight.Bold)
                        Text("${String.format("%.1f", insight.percentageOfTotal)}% of total spending", fontSize = 12.sp)
                    }
                    Text("KSh ${formatCurrency(insight.amount)}", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Savings Goal Calculator Button
        item {
            Button(
                onClick = onCalculateSavings,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp)
            ) {
                Icon(Icons.Default.Calculate, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Savings Goal Calculator")
            }
        }
    }
}

// NEW: Savings Calculator Dialog
@Composable
fun SavingsCalculatorDialog(
    onCalculate: (Double, Long) -> Unit,
    onDismiss: () -> Unit
) {
    var targetAmount by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<SavingsInsight?>(null) }
    
    // Simple date picker state (using Calendar for simplicity here)
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.MONTH, 6) // Default 6 months away
    var targetDate by remember { mutableLongStateOf(calendar.timeInMillis) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Savings Goal Calculator", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = targetAmount,
                    onValueChange = { targetAmount = it },
                    label = { Text("How much do you want to save?") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Text("KSh") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text("Target Date: ${formatDate(targetDate)}", fontSize = 14.sp)
                Button(onClick = { /* In a real app, show date picker */ }) {
                    Text("Change Target Date")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        val amt = targetAmount.toDoubleOrNull() ?: 0.0
                        if (amt > 0) {
                            val months = 6 // Hardcoded for this demo, would be calculated from date
                            result = SavingsInsight(amt, targetDate, amt / months, months)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Calculate")
                }
                
                if (result != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Monthly Savings Needed:", fontSize = 14.sp)
                            Text(
                                "KSh ${formatCurrency(result!!.monthlyRequirement)}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text("to reach KSh ${formatCurrency(result!!.targetAmount)} in ${result!!.monthsRemaining} months", fontSize = 12.sp)
                        }
                    }
                }
                
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close")
                }
            }
        }
    }
}

// NEW: Savings Goal Item
@Composable
fun SavingsGoalItem(
    goal: SavingsGoal,
    onContribute: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = goal.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    text = "Deadline: ${formatDateShort(goal.deadline)} • Priority: ${
                        when(goal.priority) {
                            3 -> "High"
                            2 -> "Medium"
                            else -> "Low"
                        }
                    }",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onContribute,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Add", fontSize = 12.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        val progress = (goal.currentAmount / goal.targetAmount).toFloat().coerceIn(0f, 1f)
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "KSh ${formatCurrency(goal.currentAmount)} / KSh ${formatCurrency(goal.targetAmount)}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// NEW: Add Savings Goal Dialog
@Composable
fun AddSavingsGoalDialog(
    onSave: (title: String, target: Double, deadline: Long, priority: Int) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var targetAmount by remember { mutableStateOf("") }
    var priority by remember { mutableIntStateOf(2) } // Default Medium
    
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.MONTH, 1)
    var deadline by remember { mutableLongStateOf(calendar.timeInMillis) }

    Dialog(onDismissRequest = onCancel) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Create Savings Goal", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Goal Title (e.g., Vacation)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = targetAmount,
                    onValueChange = { targetAmount = it },
                    label = { Text("Target Amount") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Text("KSh") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text("Priority", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1 to "Low", 2 to "Medium", 3 to "High").forEach { (p, label) ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val amt = targetAmount.toDoubleOrNull() ?: 0.0
                            if (title.isNotEmpty() && amt > 0) {
                                onSave(title, amt, deadline, priority)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = title.isNotEmpty() && targetAmount.toDoubleOrNull() != null
                    ) {
                        Text("Create")
                    }
                }
            }
        }
    }
}

// NEW: Savings Contribution Dialog
@Composable
fun SavingsContributionDialog(
    goal: SavingsGoal,
    onConfirm: (Double) -> Unit,
    onCancel: () -> Unit
) {
    var amount by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onCancel) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Add to '${goal.title}'", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Remaining: KSh ${formatCurrency(goal.targetAmount - goal.currentAmount)}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Contribution Amount") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Text("KSh") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val amt = amount.toDoubleOrNull() ?: 0.0
                            if (amt > 0) {
                                onConfirm(amt)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = amount.toDoubleOrNull() != null
                    ) {
                        Text("Contribute")
                    }
                }
            }
        }
    }
}

// NEW: Bill Reminder Item
@Composable
fun BillReminderItem(
    reminder: BillReminder,
    onPay: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = reminder.title, fontWeight = FontWeight.Medium)
            Text(
                text = "Due: ${formatDateShort(reminder.dueDate)} • KSh ${formatCurrency(reminder.amount)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(
            onClick = onPay,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.height(32.dp)
        ) {
            Text("Pay", fontSize = 12.sp)
        }
    }
}

// NEW: Add Bill Reminder Dialog
@Composable
fun AddBillReminderDialog(
    onSave: (title: String, amount: Double, dueDate: Long, category: String) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Utilities") }
    var expanded by remember { mutableStateOf(false) }
    
    val categories = listOf("Utilities", "Rent", "Insurance", "Subscription", "Other")

    Dialog(onDismissRequest = onCancel) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Add Bill Reminder", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Bill Title") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Text("KSh") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category) },
                                onClick = {
                                    selectedCategory = category
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val amt = amount.toDoubleOrNull() ?: 0.0
                            if (title.isNotEmpty() && amt > 0) {
                                // Default due date to 1 week from now for demo
                                val dueDate = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000)
                                onSave(title, amt, dueDate, selectedCategory)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = title.isNotEmpty() && amount.toDoubleOrNull() != null
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

// NEW: Settings Screen
@Composable
fun SettingsScreen(
    viewModel: BudgetViewModel,
    onDismiss: () -> Unit,
    onExportClick: () -> Unit,
    onClearData: () -> Unit,
    onManageCategories: () -> Unit
) {
    val settingsManager = viewModel.getSettingsManager()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    var darkMode by remember { mutableStateOf(settingsManager.darkMode) }
    var currency by remember { mutableStateOf(settingsManager.currency) }
    var language by remember { mutableStateOf(settingsManager.language) }
    var notificationsEnabled by remember { mutableStateOf(settingsManager.notificationsEnabled) }
    var backupEnabled by remember { mutableStateOf(settingsManager.backupEnabled) }
    var budgetDefault by remember { mutableStateOf(settingsManager.budgetDefault.toString()) }
    var dateFormat by remember { mutableStateOf(settingsManager.dateFormat) }
    var firstDayOfWeek by remember { mutableStateOf(settingsManager.firstDayOfWeek) }
    
    var pinEnabled by remember { mutableStateOf(settingsManager.pinEnabled) }
    var pinCode by remember { mutableStateOf(settingsManager.pinCode) }
    var biometricEnabled by remember { mutableStateOf(settingsManager.biometricEnabled) }
    var hideBalances by remember { mutableStateOf(settingsManager.hideBalances) }
    var privacyMode by remember { mutableStateOf(settingsManager.privacyMode) }

    val currencies = listOf("KSh", "USD", "EUR", "GBP", "JPY")
    val languages = listOf("en" to "English", "sw" to "Swahili", "fr" to "French")
    val dateFormats = listOf("MMM d, yyyy", "dd/MM/yyyy", "yyyy-MM-dd")
    val weekDays = listOf(1 to "Sunday", 2 to "Monday")

    Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .heightIn(max = 650.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Settings",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Security Settings
                item {
                    Text("Security & Privacy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable PIN Lock")
                        Switch(
                            checked = pinEnabled,
                            onCheckedChange = { 
                                pinEnabled = it
                                if (!it) viewModel.setPin("")
                            }
                        )
                    }
                    
                    if (pinEnabled) {
                        OutlinedTextField(
                            value = pinCode,
                            onValueChange = { 
                                pinCode = it
                                viewModel.setPin(it)
                            },
                            label = { Text("Set 4-Digit PIN") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Biometric Authentication")
                        Switch(
                            checked = biometricEnabled,
                            onCheckedChange = { 
                                biometricEnabled = it
                                viewModel.updateBiometricEnabled(it)
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Hide Balances on Home")
                        Switch(
                            checked = hideBalances,
                            onCheckedChange = { 
                                hideBalances = it
                                viewModel.updateHideBalances(it)
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Privacy Mode (Hide amounts)")
                        Switch(
                            checked = privacyMode,
                            onCheckedChange = { 
                                privacyMode = it
                                viewModel.updatePrivacyMode(it)
                            }
                        )
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                // Theme Settings
                item {
                    Text("Theme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Dark Mode")
                        Switch(
                            checked = darkMode,
                            onCheckedChange = { 
                                darkMode = it
                                viewModel.updateDarkMode(it)
                            }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                // Currency Settings
                item {
                    Text("Currency", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = currency,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            currencies.forEach { curr ->
                                DropdownMenuItem(
                                    text = { Text(curr) },
                                    onClick = {
                                        currency = curr
                                        viewModel.updateCurrency(curr)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                // Language Settings
                item {
                    Text("Language", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    var langExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = langExpanded,
                        onExpandedChange = { langExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = languages.find { it.first == language }?.second ?: "English",
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = langExpanded,
                            onDismissRequest = { langExpanded = false }
                        ) {
                            languages.forEach { (code, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        language = code
                                        viewModel.updateLanguage(code)
                                        langExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                // Notification Settings
                item {
                    Text("Notifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable Notifications")
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { 
                                notificationsEnabled = it
                                viewModel.updateNotificationsEnabled(it)
                            }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                // Backup Settings
                item {
                    Text("Backup & Sync", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Automatic Cloud Backup")
                        Switch(
                            checked = backupEnabled,
                            onCheckedChange = { 
                                backupEnabled = it
                                viewModel.updateBackupEnabled(it)
                            }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                // Budget Defaults
                item {
                    Text("Budget Defaults", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = budgetDefault,
                        onValueChange = { 
                            budgetDefault = it
                            it.toDoubleOrNull()?.let { amt -> viewModel.updateBudgetDefault(amt) }
                        },
                        label = { Text("Default Monthly Budget") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                // Date Format
                item {
                    Text("Date Format", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    var dateExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = dateExpanded,
                        onExpandedChange = { dateExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = dateFormat,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dateExpanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = dateExpanded,
                            onDismissRequest = { dateExpanded = false }
                        ) {
                            dateFormats.forEach { format ->
                                DropdownMenuItem(
                                    text = { Text(format) },
                                    onClick = {
                                        dateFormat = format
                                        viewModel.updateDateFormat(format)
                                        dateExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                // First Day of Week
                item {
                    Text("First Day of Week", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        weekDays.forEach { (value, label) ->
                            FilterChip(
                                selected = firstDayOfWeek == value,
                                onClick = { 
                                    firstDayOfWeek = value
                                    viewModel.updateFirstDayOfWeek(value)
                                },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                // Data Management Section
                item {
                    Text("Data Management", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.archiveOldData() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Archive Old", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.restoreArchivedData() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Restore All", fontSize = 12.sp)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.resetBudgets() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reset Budgets", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.checkDataIntegrity() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Check Integrity", fontSize = 12.sp)
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                // Category Management
                item {
                    Button(
                        onClick = onManageCategories,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Category, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Manage Categories")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Data Operations
                item {
                    Text("Export Options", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onExportClick,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("CSV", fontSize = 12.sp)
                        }
                        
                        Button(
                            onClick = {
                                scope.launch {
                                    val data = viewModel.exportToExcel()
                                    val file = File(context.filesDir, "budget_excel_${System.currentTimeMillis()}.xls")
                                    file.writeText(data)
                                    Toast.makeText(context, "Exported to ${file.name}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Excel", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    val data = viewModel.exportToPDF()
                                    val file = File(context.filesDir, "budget_report_${System.currentTimeMillis()}.pdf")
                                    file.writeText(data) // In real app, write bytes of PDF
                                    Toast.makeText(context, "Report generated", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("PDF", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = onClearData,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear All Data")
                    }
                }
            }
        }
    }
}

// NEW: Manage Categories Dialog
@Composable
fun ManageCategoriesDialog(
    categories: List<Category>,
    onAdd: (String, String) -> Unit,
    onDelete: (Category) -> Unit,
    onDismiss: () -> Unit
) {
    var newCategoryName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("EXPENSE") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(max = 500.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Manage Categories", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = selectedType == "EXPENSE",
                        onClick = { selectedType = "EXPENSE" },
                        label = { Text("Expense") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = selectedType == "INCOME",
                        onClick = { selectedType = "INCOME" },
                        label = { Text("Income") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text("New Category") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    IconButton(
                        onClick = {
                            if (newCategoryName.isNotBlank()) {
                                onAdd(newCategoryName, selectedType)
                                newCategoryName = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(categories.filter { it.type == selectedType }) { category ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(category.name)
                            IconButton(onClick = { onDelete(category) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        HorizontalDivider()
                    }
                }

                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close")
                }
            }
        }
    }
}


// NEW: Delete Confirmation Dialog
@Composable
fun DeleteConfirmationDialog(
    transaction: Transaction,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Dialog(
        onDismissRequest = onCancel
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Delete Transaction?",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Are you sure you want to delete this transaction?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "${transaction.category}: ${if (transaction.type == "EXPENSE") "-" else "+"}KSh ${formatCurrency(transaction.amount)}",
                            fontWeight = FontWeight.Medium
                        )
                        if (transaction.note.isNotEmpty()) {
                            Text(
                                text = transaction.note,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = formatDate(transaction.date),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete")
                    }
                }
            }
        }
    }
}
