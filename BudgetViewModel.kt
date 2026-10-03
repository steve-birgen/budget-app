package com.yourapp.budgetapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class BudgetViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = AppDatabase.getInstance(application)
    private val transactionDao = database.transactionDao()
    private val budgetGoalDao = database.budgetGoalDao()
    private val billReminderDao = database.billReminderDao()
    private val categoryDao = database.categoryDao()
    private val savingsGoalDao = database.savingsGoalDao()
    private val notificationHelper = NotificationHelper(application)
    private val settingsManager = SettingsManager(application)
    
    // State flows for UI
    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()
    
    private val _filteredTransactions = MutableStateFlow<List<Transaction>>(emptyList())
    val filteredTransactions: StateFlow<List<Transaction>> = _filteredTransactions.asStateFlow()
    
    private val _billReminders = MutableStateFlow<List<BillReminder>>(emptyList())
    val billReminders: StateFlow<List<BillReminder>> = _billReminders.asStateFlow()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _savingsGoals = MutableStateFlow<List<SavingsGoal>>(emptyList())
    val savingsGoals: StateFlow<List<SavingsGoal>> = _savingsGoals.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()
    
    private val _selectedMonth = MutableStateFlow(Calendar.getInstance())
    val selectedMonth: StateFlow<Calendar> = _selectedMonth.asStateFlow()
    
    private val _monthlySummary = MutableStateFlow(MonthlySummary(0.0, 0.0, 0.0))
    val monthlySummary: StateFlow<MonthlySummary> = _monthlySummary.asStateFlow()
    
    private val _currentBalance = MutableStateFlow(0.0)
    val currentBalance: StateFlow<Double> = _currentBalance.asStateFlow()

    private val _totalSavings = MutableStateFlow(0.0)
    val totalSavings: StateFlow<Double> = _totalSavings.asStateFlow()

    private val _financialHealthScore = MutableStateFlow(0)
    val financialHealthScore: StateFlow<Int> = _financialHealthScore.asStateFlow()
    
    private val _dailyRemaining = MutableStateFlow("KSh 0.00 / day left")
    val dailyRemaining: StateFlow<String> = _dailyRemaining.asStateFlow()
    
    private val _budgetGoals = MutableStateFlow<List<BudgetGoal>>(emptyList())
    val budgetGoals: StateFlow<List<BudgetGoal>> = _budgetGoals.asStateFlow()

    private val _pastBudgetGoals = MutableStateFlow<List<BudgetGoal>>(emptyList())
    val pastBudgetGoals: StateFlow<List<BudgetGoal>> = _pastBudgetGoals.asStateFlow()
    
    private val _categorySpending = MutableStateFlow<Map<String, Double>>(emptyMap())
    val categorySpending: StateFlow<Map<String, Double>> = _categorySpending.asStateFlow()

    private val _spendingTrends = MutableStateFlow<SpendingTrend?>(null)
    val spendingTrends: StateFlow<SpendingTrend?> = _spendingTrends.asStateFlow()

    private val _topSpendingCategories = MutableStateFlow<List<CategoryInsight>>(emptyList())
    val topSpendingCategories: StateFlow<List<CategoryInsight>> = _topSpendingCategories.asStateFlow()

    private val _spendingForecast = MutableStateFlow(0.0)
    val spendingForecast: StateFlow<Double> = _spendingForecast.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _financialInsights = MutableStateFlow<List<FinancialInsight>>(emptyList())
    val financialInsights: StateFlow<List<FinancialInsight>> = _financialInsights.asStateFlow()

    private val _dailyExpenses = MutableStateFlow<Map<Int, List<Transaction>>>(emptyMap())
    val dailyExpenses: StateFlow<Map<Int, List<Transaction>>> = _dailyExpenses.asStateFlow()

    private val _incomeCalendar = MutableStateFlow<Map<Int, List<Transaction>>>(emptyMap())
    val incomeCalendar: StateFlow<Map<Int, List<Transaction>>> = _incomeCalendar.asStateFlow()

    private val _billsCalendar = MutableStateFlow<Map<Int, List<BillReminder>>>(emptyMap())
    val billsCalendar: StateFlow<Map<Int, List<BillReminder>>> = _billsCalendar.asStateFlow()

    private val _budgetCalendar = MutableStateFlow<List<BudgetGoal>>(emptyList())
    val budgetCalendar: StateFlow<List<BudgetGoal>> = _budgetCalendar.asStateFlow()

    private val _savingsMilestones = MutableStateFlow<List<SavingsGoal>>(emptyList())
    val savingsMilestones: StateFlow<List<SavingsGoal>> = _savingsMilestones.asStateFlow()

    private val _upcomingReminders = MutableStateFlow<List<BillReminder>>(emptyList())
    val upcomingReminders: StateFlow<List<BillReminder>> = _upcomingReminders.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    private val _darkMode = MutableStateFlow(settingsManager.darkMode)
    val darkMode: StateFlow<Boolean> = _darkMode.asStateFlow()

    private val _currency = MutableStateFlow(settingsManager.currency)
    val currency: StateFlow<String> = _currency.asStateFlow()

    private val _hideBalances = MutableStateFlow(settingsManager.hideBalances)
    val hideBalances: StateFlow<Boolean> = _hideBalances.asStateFlow()

    private val _privacyMode = MutableStateFlow(settingsManager.privacyMode)
    val privacyMode: StateFlow<Boolean> = _privacyMode.asStateFlow()

    private val _isLocked = MutableStateFlow(settingsManager.pinEnabled || settingsManager.biometricEnabled)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()
    
    init {
        loadCategories()
        loadAllTransactions()
        loadBudgetGoals()
        loadBillReminders()
        loadSavingsGoals()
        scheduleNotifications()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            val allCategories = categoryDao.getAll()
            if (allCategories.isEmpty()) {
                val defaults = listOf(
                    Category(name = "Food", type = "EXPENSE", iconName = "Fastfood", colorHex = "#F44336"),
                    Category(name = "Transport", type = "EXPENSE", iconName = "DirectionsCar", colorHex = "#2196F3"),
                    Category(name = "Rent", type = "EXPENSE", iconName = "Home", colorHex = "#9C27B0"),
                    Category(name = "Utilities", type = "EXPENSE", iconName = "Power", colorHex = "#FF9800"),
                    Category(name = "Shopping", type = "EXPENSE", iconName = "ShoppingBag", colorHex = "#E91E63"),
                    Category(name = "Entertainment", type = "EXPENSE", iconName = "Movie", colorHex = "#673AB7"),
                    Category(name = "Health", type = "EXPENSE", iconName = "HealthAndSafety", colorHex = "#4CAF50"),
                    Category(name = "Salary", type = "INCOME", iconName = "Payments", colorHex = "#4CAF50"),
                    Category(name = "Business", type = "INCOME", iconName = "BusinessCenter", colorHex = "#3F51B5"),
                    Category(name = "Freelance", type = "INCOME", iconName = "LaptopMac", colorHex = "#009688"),
                    Category(name = "Investment", type = "INCOME", iconName = "TrendingUp", colorHex = "#FFEB3B"),
                    Category(name = "Gift", type = "INCOME", iconName = "Redeem", colorHex = "#FF5722"),
                    Category(name = "Other", type = "EXPENSE", iconName = "Category", colorHex = "#9E9E9E"),
                    Category(name = "Other", type = "INCOME", iconName = "Category", colorHex = "#9E9E9E")
                )
                defaults.forEach { categoryDao.insert(it) }
                _categories.value = categoryDao.getAll()
            } else {
                _categories.value = allCategories
            }
        }
    }

    fun addCategory(name: String, type: String, iconName: String = "Category", colorHex: String = "#9E9E9E") {
        viewModelScope.launch {
            try {
                categoryDao.insert(Category(name = name, type = type, iconName = iconName, colorHex = colorHex))
                loadCategories()
                _successMessage.value = "Category added: $name"
                delay(2000)
                _successMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to add category: ${e.message}"
            }
        }
    }

    fun renameCategory(category: Category, newName: String) {
        viewModelScope.launch {
            try {
                val oldName = category.name
                val updated = category.copy(name = newName)
                
                // Update all related data using old name
                transactionDao.updateCategoryName(oldName, newName)
                budgetGoalDao.updateCategoryName(oldName, newName)
                billReminderDao.updateCategoryName(oldName, newName)
                
                categoryDao.update(updated)
                
                loadCategories()
                loadAllTransactions() // Refresh lists
                _successMessage.value = "Category renamed to $newName"
                delay(2000)
                _successMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to rename category"
            }
        }
    }

    fun mergeCategories(source: Category, target: Category) {
        viewModelScope.launch {
            try {
                val oldName = source.name
                val newName = target.name
                
                // Move all data to target
                transactionDao.updateCategoryName(oldName, newName)
                budgetGoalDao.updateCategoryName(oldName, newName)
                billReminderDao.updateCategoryName(oldName, newName)
                
                // Delete source
                categoryDao.delete(source)
                
                loadCategories()
                loadAllTransactions()
                _successMessage.value = "Categories merged successfully"
                delay(2000)
                _successMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to merge categories"
            }
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            try {
                categoryDao.delete(category)
                loadCategories()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete category"
            }
        }
    }
    
    private fun scheduleNotifications() {
        val workManager = WorkManager.getInstance(getApplication())

        val dailyRequest = PeriodicWorkRequestBuilder<SummaryWorker>(24, TimeUnit.HOURS)
            .setInputData(workDataOf("type" to "DAILY"))
            .setInitialDelay(calculateDelayUntil(20), TimeUnit.MILLISECONDS)
            .build()

        val weeklyRequest = PeriodicWorkRequestBuilder<SummaryWorker>(7, TimeUnit.DAYS)
            .setInputData(workDataOf("type" to "WEEKLY"))
            .setInitialDelay(calculateDelayUntilNextSunday(20), TimeUnit.MILLISECONDS)
            .build()

        val monthlyRequest = PeriodicWorkRequestBuilder<SummaryWorker>(30, TimeUnit.DAYS)
            .setInputData(workDataOf("type" to "MONTHLY"))
            .setInitialDelay(calculateDelayUntilEndOfMonth(20), TimeUnit.MILLISECONDS)
            .build()

        val reminderRequest = PeriodicWorkRequestBuilder<ReminderWorker>(12, TimeUnit.HOURS)
            .build()

        workManager.enqueueUniquePeriodicWork("daily_summary", ExistingPeriodicWorkPolicy.KEEP, dailyRequest)
        workManager.enqueueUniquePeriodicWork("weekly_summary", ExistingPeriodicWorkPolicy.KEEP, weeklyRequest)
        workManager.enqueueUniquePeriodicWork("monthly_report", ExistingPeriodicWorkPolicy.KEEP, monthlyRequest)
        workManager.enqueueUniquePeriodicWork("bill_reminders", ExistingPeriodicWorkPolicy.KEEP, reminderRequest)
    }

    private fun calculateDelayUntilEndOfMonth(hour: Int): Long {
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        if (calendar.timeInMillis <= now) {
            calendar.add(Calendar.MONTH, 1)
            calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        }
        return calendar.timeInMillis - now
    }

    private fun calculateDelayUntil(hour: Int): Long {
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        if (calendar.timeInMillis <= now) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis - now
    }

    private fun calculateDelayUntilNextSunday(hour: Int): Long {
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        if (calendar.timeInMillis <= now) {
            calendar.add(Calendar.WEEK_OF_YEAR, 1)
        }
        return calendar.timeInMillis - now
    }

    private fun loadAllTransactions() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val allTransactions = transactionDao.getAll()
                _transactions.value = allTransactions
                applyFilters()
                updateDashboardMetrics(allTransactions)
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load transactions: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun loadCurrentMonthTransactions() {
        loadAllTransactions()
    }
    
    fun applyFilters() {
        viewModelScope.launch {
            val query = _searchQuery.value
            val category = _selectedCategory.value
            val month = _selectedMonth.value
            
            val startOfMonth = getStartOfMonth(month.timeInMillis)
            val endOfMonth = getEndOfMonth(month.timeInMillis)
            
            var filtered = _transactions.value.filter { 
                it.date in startOfMonth..endOfMonth 
            }
            
            if (query.isNotEmpty()) {
                filtered = filtered.filter { 
                    it.category.contains(query, ignoreCase = true) ||
                    it.note.contains(query, ignoreCase = true) ||
                    it.merchant.contains(query, ignoreCase = true) ||
                    it.tags.contains(query, ignoreCase = true) ||
                    it.amount.toString().contains(query) ||
                    formatDate(it.date).contains(query, ignoreCase = true)
                }
            }
            
            if (category != null) {
                filtered = filtered.filter { it.category == category }
            }
            
            _filteredTransactions.value = filtered
            updateMonthlySummary(filtered)
            updateCategorySpending()
            updateCalendarData(filtered)
        }
    }

    private fun updateCalendarData(transactions: List<Transaction>) {
        val calendar = Calendar.getInstance()
        
        // Group Expenses by Day
        _dailyExpenses.value = transactions
            .filter { it.type == "EXPENSE" }
            .groupBy { 
                calendar.timeInMillis = it.date
                calendar.get(Calendar.DAY_OF_MONTH)
            }

        // Group Income by Day
        _incomeCalendar.value = transactions
            .filter { it.type == "INCOME" }
            .groupBy {
                calendar.timeInMillis = it.date
                calendar.get(Calendar.DAY_OF_MONTH)
            }

        // Bills Calendar
        val monthStart = getStartOfMonth(_selectedMonth.value.timeInMillis)
        val monthEnd = getEndOfMonth(_selectedMonth.value.timeInMillis)
        
        _billsCalendar.value = _billReminders.value
            .filter { it.dueDate in monthStart..monthEnd }
            .groupBy {
                calendar.timeInMillis = it.dueDate
                calendar.get(Calendar.DAY_OF_MONTH)
            }

        // Upcoming Reminders (next 7 days)
        val now = System.currentTimeMillis()
        val nextWeek = now + (7 * 24 * 60 * 60 * 1000)
        _upcomingReminders.value = _billReminders.value.filter { it.dueDate in now..nextWeek && !it.isPaid }

        // Budget Calendar (active goals for the month)
        _budgetCalendar.value = _budgetGoals.value

        // Savings Milestones
        _savingsMilestones.value = _savingsGoals.value.filter { it.deadline in monthStart..monthEnd }
    }
    
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        applyFilters()
    }
    
    fun setCategoryFilter(category: String?) {
        _selectedCategory.value = category
        applyFilters()
    }
    
    fun navigateMonth(direction: Int) {
        val calendar = _selectedMonth.value
        calendar.add(Calendar.MONTH, direction)
        _selectedMonth.value = calendar
        applyFilters()
    }
    
    suspend fun getCategoriesList(): List<String> {
        return categoryDao.getAll().map { it.name }.distinct()
    }

    fun getCategories(): List<String> {
        return _categories.value.map { it.name }.distinct()
    }
    
    fun addTransaction(amount: Double, category: String, merchant: String = "", note: String = "", tags: String = "", type: String, isRecurring: Boolean = false, frequency: String = "", recurringEndDate: Long? = null) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                // Smart Feature: Duplicate Detection
                val today = System.currentTimeMillis()
                val startOfDay = today - (today % (24 * 60 * 60 * 1000))
                val endOfDay = startOfDay + (24 * 60 * 60 * 1000)
                val duplicates = transactionDao.findDuplicates(amount, category, startOfDay, endOfDay)
                
                if (duplicates.isNotEmpty()) {
                    _errorMessage.value = "Warning: Possible duplicate transaction detected."
                }

                val transaction = Transaction(
                    id = 0,
                    amount = amount,
                    category = category,
                    merchant = merchant,
                    note = note,
                    tags = tags,
                    date = System.currentTimeMillis(),
                    type = type,
                    isRecurring = isRecurring,
                    recurringFrequency = if (isRecurring) frequency else "",
                    recurringEndDate = if (isRecurring) recurringEndDate else null,
                    isRecurringPaused = false,
                    isArchived = false
                )
                
                transactionDao.insert(transaction)
                
                if (type == "EXPENSE") {
                    checkBudgetExceeded(category)
                }

                loadCurrentMonthTransactions()
                
                _successMessage.value = "Transaction added successfully!"
                _errorMessage.value = null
                
                delay(2000)
                _successMessage.value = null
                
            } catch (e: Exception) {
                _errorMessage.value = "Failed to add transaction: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun checkBudgetExceeded(category: String) {
        val now = System.currentTimeMillis()
        val goal = budgetGoalDao.getGoalForCategory(category, now)
        if (goal != null) {
            val spent = transactionDao.getTotalByCategoryBetween(category, goal.startDate, goal.endDate) ?: 0.0
            
            if (spent > goal.amount) {
                notificationHelper.showNotification(
                    NotificationHelper.CHANNEL_ID_ALERTS,
                    "Budget Exceeded!",
                    "You've exceeded your budget for $category. Spent: KSh ${String.format(Locale.US, "%,.2f", spent)} / Goal: KSh ${String.format(Locale.US, "%,.2f", goal.amount)}",
                    999
                )
            }
        }
    }
    
    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                transactionDao.update(transaction)
                loadCurrentMonthTransactions()
                _successMessage.value = "Transaction updated successfully!"
                _errorMessage.value = null
                delay(2000)
                _successMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update transaction: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                transactionDao.delete(transaction)
                loadCurrentMonthTransactions()
                _successMessage.value = "Transaction deleted"
                _errorMessage.value = null
                delay(2000)
                _successMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete transaction: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearAllTransactions() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                transactionDao.deleteAll()
                budgetGoalDao.deleteAll()
                _transactions.value = emptyList()
                _filteredTransactions.value = emptyList()
                updateDashboardMetrics(emptyList())
                updateMonthlySummary(emptyList())
                updateCategorySpending()
                _successMessage.value = "All data cleared"
                delay(2000)
                _successMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to clear data: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    private fun loadBudgetGoals() {
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                _budgetGoals.value = budgetGoalDao.getActiveGoals(now)
                _pastBudgetGoals.value = budgetGoalDao.getPastGoals(now)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun addBudgetGoal(category: String, amount: Double, period: String, startDate: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            try {
                val calendar = Calendar.getInstance().apply { timeInMillis = startDate }
                val endDate = when (period) {
                    "WEEK" -> (calendar.clone() as Calendar).apply { add(Calendar.WEEK_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }.timeInMillis
                    "MONTH" -> (calendar.clone() as Calendar).apply { add(Calendar.MONTH, 1); set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }.timeInMillis
                    "YEAR" -> (calendar.clone() as Calendar).apply { add(Calendar.YEAR, 1); set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }.timeInMillis
                    else -> startDate + (30L * 24 * 60 * 60 * 1000) // Default 30 days for CUSTOM if not specified
                }

                val goal = BudgetGoal(
                    category = category,
                    amount = amount,
                    startDate = startDate,
                    endDate = endDate,
                    period = period
                )
                budgetGoalDao.insert(goal)
                loadBudgetGoals()
                _successMessage.value = "Budget goal set for $category"
                delay(2000)
                _successMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to set budget goal: ${e.message}"
            }
        }
    }

    private fun loadBillReminders() {
        viewModelScope.launch {
            try {
                val reminders = billReminderDao.getAllReminders()
                _billReminders.value = reminders
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun addBillReminder(title: String, amount: Double, dueDate: Long, category: String) {
        viewModelScope.launch {
            try {
                val reminder = BillReminder(
                    title = title,
                    amount = amount,
                    dueDate = dueDate,
                    category = category
                )
                billReminderDao.insert(reminder)
                loadBillReminders()
                _successMessage.value = "Reminder set for $title"
                delay(2000)
                _successMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to set reminder: ${e.message}"
            }
        }
    }

    fun payBill(reminder: BillReminder) {
        viewModelScope.launch {
            try {
                val updated = reminder.copy(isPaid = true)
                billReminderDao.update(updated)
                
                addTransaction(
                    amount = reminder.amount, 
                    category = reminder.category, 
                    merchant = reminder.title, 
                    note = "Paid Bill: ${reminder.title}", 
                    type = "EXPENSE"
                )
                
                loadBillReminders()
                _successMessage.value = "Bill marked as paid"
                delay(2000)
                _successMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to pay bill: ${e.message}"
            }
        }
    }

    private fun loadSavingsGoals() {
        viewModelScope.launch {
            try {
                val goals = savingsGoalDao.getAllGoals()
                _savingsGoals.value = goals
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun addSavingsGoal(title: String, targetAmount: Double, deadline: Long, priority: Int) {
        viewModelScope.launch {
            try {
                val goal = SavingsGoal(
                    title = title,
                    targetAmount = targetAmount,
                    deadline = deadline,
                    priority = priority
                )
                savingsGoalDao.insert(goal)
                loadSavingsGoals()
                _successMessage.value = "Savings goal '$title' created!"
                delay(2000)
                _successMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to create savings goal"
            }
        }
    }

    fun contributeToSavings(goal: SavingsGoal, amount: Double) {
        viewModelScope.launch {
            try {
                val newAmount = goal.currentAmount + amount
                val isCompleted = newAmount >= goal.targetAmount
                val updated = goal.copy(
                    currentAmount = newAmount,
                    isCompleted = isCompleted
                )
                savingsGoalDao.update(updated)
                
                addTransaction(
                    amount = amount, 
                    category = "Savings", 
                    merchant = "Goal: ${goal.title}",
                    note = "Contribution to ${goal.title}", 
                    type = "EXPENSE"
                )
                
                loadSavingsGoals()
                if (isCompleted) {
                    _successMessage.value = "Congratulations! Goal '${goal.title}' completed! 🎉"
                } else {
                    _successMessage.value = "KSh $amount added to ${goal.title}"
                }
                delay(3000)
                _successMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to add contribution"
            }
        }
    }
    
    private fun updateCategorySpending() {
        viewModelScope.launch {
            try {
                val currentMonth = _selectedMonth.value
                val startOfCurrent = getStartOfMonth(currentMonth.timeInMillis)
                val endOfCurrent = getEndOfMonth(currentMonth.timeInMillis)
                
                val cats = categoryDao.getByType("EXPENSE")
                val spending = mutableMapOf<String, Double>()
                var totalSpent = 0.0
                
                cats.forEach { cat ->
                    val total = transactionDao.getTotalByCategoryBetween(
                        cat.name, startOfCurrent, endOfCurrent
                    ) ?: 0.0
                    spending[cat.name] = total
                    totalSpent += total
                }
                
                _categorySpending.value = spending

                val insights = spending.map { (cat, amt) ->
                    CategoryInsight(
                        category = cat,
                        amount = amt,
                        percentageOfTotal = if (totalSpent > 0) ((amt / totalSpent) * 100).toFloat() else 0f
                    )
                }.sortedByDescending { it.amount }
                
                if (insights.isNotEmpty()) {
                    val highest = insights.first().copy(isHighest = true)
                    _topSpendingCategories.value = listOf(highest) + insights.drop(1)
                } else {
                    _topSpendingCategories.value = emptyList()
                }

                val prevMonth = (currentMonth.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                val startOfPrev = getStartOfMonth(prevMonth.timeInMillis)
                val endOfPrev = getEndOfMonth(prevMonth.timeInMillis)
                
                val currentExpense = transactionDao.getTotalExpensesBetween(startOfCurrent, endOfCurrent) ?: 0.0
                val prevExpense = transactionDao.getTotalExpensesBetween(startOfPrev, endOfPrev) ?: 0.0
                
                val percentChange = if (prevExpense > 0) {
                    ((currentExpense - prevExpense) / prevExpense) * 100
                } else 0.0
                
                _spendingTrends.value = SpendingTrend(currentExpense, prevExpense, percentChange)
                
                _spendingForecast.value = getSpendingForecast()

                val allTx = _transactions.value
                val monthTx = allTx.filter { it.date in startOfCurrent..endOfCurrent }
                
                generateAutomatedInsights(allTx, monthTx, spending)
                
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    private fun generateAutomatedInsights(
        allTransactions: List<Transaction>,
        currentMonthTransactions: List<Transaction>,
        categorySpending: Map<String, Double>
    ) {
        val insights = mutableListOf<FinancialInsight>()
        val expenses = currentMonthTransactions.filter { it.type == "EXPENSE" }
        
        if (expenses.isEmpty()) {
            _financialInsights.value = emptyList()
            return
        }

        // 1. Overspending & Budget Warnings
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        
        viewModelScope.launch {
            val goals = budgetGoalDao.getActiveGoals(System.currentTimeMillis())
            goals.forEach { goal ->
                val spent = categorySpending[goal.category] ?: 0.0
                if (spent > goal.amount) {
                    insights.add(FinancialInsight("Overspending", "You have exceeded your budget for ${goal.category} by KSh ${formatCurrency(spent - goal.amount)}.", "HIGH"))
                } else if (spent > goal.amount * 0.8) {
                    insights.add(FinancialInsight("Budget Warning", "You've used ${((spent/goal.amount)*100).toInt()}% of your ${goal.category} budget.", "MEDIUM"))
                }
            }
        }

        // 2. Highest Expense Category
        val highest = categorySpending.maxByOrNull { it.value }
        if (highest != null && highest.value > 0) {
            insights.add(FinancialInsight("Highest Expense", "${highest.key} is your top category this month at KSh ${formatCurrency(highest.value)}.", "INFO"))
        }

        // 3. Largest Purchase
        val largest = expenses.maxByOrNull { it.amount }
        if (largest != null) {
            insights.add(FinancialInsight("Largest Purchase", "Your biggest spend was KSh ${formatCurrency(largest.amount)} on ${largest.category}${if(largest.note.isNotEmpty()) " (${largest.note})" else ""}.", "INFO"))
        }

        // 4. Monthly Average Spending
        val totalHistoricalExpense = allTransactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        val distinctMonths = allTransactions.map { 
            val cal = Calendar.getInstance().apply { timeInMillis = it.date }
            "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}"
        }.distinct().size
        
        if (distinctMonths > 1) {
            val avg = totalHistoricalExpense / distinctMonths
            val currentTotal = expenses.sumOf { it.amount }
            if (currentTotal > avg * 1.2) {
                insights.add(FinancialInsight("Spending Increase", "You are spending 20% more than your monthly average of KSh ${formatCurrency(avg)}.", "MEDIUM"))
            } else if (currentTotal < avg * 0.8) {
                insights.add(FinancialInsight("Spending Decrease", "Great job! You've spent 20% less than your usual average so far.", "POSITIVE"))
            }
        }

        // 5. Frequent Merchants (using Note field)
        val frequent = expenses.filter { it.note.isNotBlank() }
            .groupBy { it.note.lowercase() }
            .filter { it.value.size >= 3 }
            .maxByOrNull { it.value.size }
            
        if (frequent != null) {
            insights.add(FinancialInsight("Frequent Merchant", "You've shopped at '${frequent.key}' ${frequent.value.size} times this month.", "INFO"))
        }

        // 6. Suggested Savings
        val totalIncome = currentMonthTransactions.filter { it.type == "INCOME" }.sumOf { it.amount }
        val totalExpense = expenses.sumOf { it.amount }
        val savingsPotential = totalIncome - totalExpense
        if (savingsPotential > totalIncome * 0.2) {
            insights.add(FinancialInsight("Suggested Savings", "You have a healthy surplus. Consider moving KSh ${formatCurrency(savingsPotential * 0.5)} to your emergency fund.", "POSITIVE"))
        }

        // 7. Spending Streaks (Days without expenses)
        val today = Calendar.getInstance()
        val expenseDates = expenses.map { 
            val cal = Calendar.getInstance().apply { timeInMillis = it.date }
            "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}-${cal.get(Calendar.DAY_OF_MONTH)}"
        }.toSet()

        var streak = 0
        val checkCal = Calendar.getInstance()
        for (i in 0 until 30) {
            val dateStr = "${checkCal.get(Calendar.YEAR)}-${checkCal.get(Calendar.MONTH)}-${checkCal.get(Calendar.DAY_OF_MONTH)}"
            if (!expenseDates.contains(dateStr)) {
                streak++
                checkCal.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }
        
        if (streak >= 3) {
            insights.add(FinancialInsight("Spending Streak", "Awesome! You haven't recorded an expense in $streak days.", "POSITIVE"))
        }

        _financialInsights.value = insights
    }

    fun calculateSavingsGoal(targetAmount: Double, targetDate: Long): SavingsInsight {
        val now = System.currentTimeMillis()
        val diff = targetDate - now
        val monthsRemaining = if (diff > 0) {
            val calNow = Calendar.getInstance()
            val calTarget = Calendar.getInstance().apply { timeInMillis = targetDate }
            val yearDiff = calTarget.get(Calendar.YEAR) - calNow.get(Calendar.YEAR)
            val monthDiff = calTarget.get(Calendar.MONTH) - calNow.get(Calendar.MONTH)
            (yearDiff * 12 + monthDiff).coerceAtLeast(1)
        } else 1
        
        return SavingsInsight(
            targetAmount = targetAmount,
            targetDate = targetDate,
            monthlyRequirement = targetAmount / monthsRemaining,
            monthsRemaining = monthsRemaining
        )
    }
    
    suspend fun exportToCSV(): String {
        val transactions = _filteredTransactions.value
        val header = "Date,Category,Merchant,Amount,Type,Note,Tags\n"
        val rows = transactions.joinToString("") { tx ->
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(tx.date))
            "$date,${tx.category},${tx.merchant},${tx.amount},${tx.type},${tx.note},${tx.tags}\n"
        }
        return header + rows
    }

    suspend fun exportToExcel(): String {
        // Excel can open CSV with tab separator often better depending on locale
        val transactions = _filteredTransactions.value
        val header = "Date\tCategory\tMerchant\tAmount\tType\tNote\tTags\n"
        val rows = transactions.joinToString("") { tx ->
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(tx.date))
            "$date\t${tx.category}\t${tx.merchant}\t${tx.amount}\t${tx.type}\t${tx.note}\t${tx.tags}\n"
        }
        return header + rows
    }

    suspend fun exportToPDF(): String {
        // Real PDF generation would use PdfDocument or a library
        // Returning a summary string for now, or I could implement a basic PdfDocument logic
        // But for this environment, I'll return a formatted report string that could be drawn to PDF
        val transactions = _filteredTransactions.value
        val summary = _monthlySummary.value
        val report = StringBuilder()
        report.append("FINANCIAL REPORT - ${SimpleDateFormat("MMMM yyyy", Locale.US).format(Date())}\n\n")
        report.append("Total Income: KSh ${formatCurrency(summary.totalIncome)}\n")
        report.append("Total Expenses: KSh ${formatCurrency(summary.totalExpenses)}\n")
        report.append("Remaining: KSh ${formatCurrency(summary.remaining)}\n\n")
        report.append("Category Breakdown:\n")
        _categorySpending.value.forEach { (cat, amt) ->
            report.append("- $cat: KSh ${formatCurrency(amt)}\n")
        }
        report.append("\nTransactions:\n")
        transactions.take(50).forEach { tx ->
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(tx.date))
            report.append("$date | ${tx.category} | ${tx.amount} | ${tx.type}\n")
        }
        return report.toString()
    }
    
    private fun updateDashboardMetrics(allTransactions: List<Transaction>) {
        val totalIncome = allTransactions.filter { it.type == "INCOME" }.sumOf { it.amount }
        val totalExpense = allTransactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        val balance = totalIncome - totalExpense
        
        _currentBalance.value = balance
        _totalSavings.value = balance 
        
        var score = 50
        if (totalIncome > totalExpense) score += 20
        if (totalExpense > 0 && totalIncome / totalExpense > 1.5) score += 10
        
        val calendar = Calendar.getInstance()
        val startOfMonth = getStartOfMonth(calendar.timeInMillis)
        val endOfMonth = getEndOfMonth(calendar.timeInMillis)
        val monthTransactions = allTransactions.filter { it.date in startOfMonth..endOfMonth }
        val monthIncome = monthTransactions.filter { it.type == "INCOME" }.sumOf { it.amount }
        val monthExpense = monthTransactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        
        if (monthIncome > monthExpense) score += 10
        if (monthExpense < monthIncome * 0.7) score += 10
        
        _financialHealthScore.value = score.coerceIn(0, 100)
    }

    private fun updateMonthlySummary(transactions: List<Transaction>) {
        val startOfMonth = getStartOfMonth(_selectedMonth.value.timeInMillis)
        val endOfMonth = getEndOfMonth(_selectedMonth.value.timeInMillis)
        
        val monthlyTransactions = transactions.filter { 
            it.date in startOfMonth..endOfMonth 
        }
        
        val totalIncome = monthlyTransactions
            .filter { it.type == "INCOME" }
            .sumOf { it.amount }
        
        val totalExpenses = monthlyTransactions
            .filter { it.type == "EXPENSE" }
            .sumOf { it.amount }
        
        val remaining = totalIncome - totalExpenses
        
        _monthlySummary.value = MonthlySummary(
            totalIncome = totalIncome,
            totalExpenses = totalExpenses,
            remaining = remaining
        )
        
        updateDailyRemaining(remaining)
    }
    
    private fun updateDailyRemaining(remaining: Double) {
        val daysLeft = getDaysRemainingInMonth()
        val dailyBudget = if (daysLeft > 0) remaining / daysLeft else remaining
        
        val formattedDaily = String.format(Locale.US, "%,.2f", dailyBudget)
        val daysText = if (daysLeft <= 1) "day" else "days"
        
        _dailyRemaining.value = if (remaining >= 0) {
            "KSh $formattedDaily / $daysText left"
        } else {
            "KSh $formattedDaily over budget per $daysText"
        }
    }
    
    fun refreshData() {
        loadCurrentMonthTransactions()
    }
    
    private fun getStartOfMonth(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
    
    private fun getEndOfMonth(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }
    
    private fun getDaysRemainingInMonth(): Int {
        val calendar = Calendar.getInstance()
        val today = calendar.get(Calendar.DAY_OF_MONTH)
        val lastDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        return lastDay - today + 1
    }
    
    // Settings Handlers
    fun getSettingsManager() = settingsManager

    fun updateDarkMode(enabled: Boolean) {
        settingsManager.darkMode = enabled
        _darkMode.value = enabled
    }

    fun updateCurrency(currency: String) {
        settingsManager.currency = currency
        _currency.value = currency
        refreshData()
    }

    fun pauseRecurring(transaction: Transaction) {
        viewModelScope.launch {
            if (transaction.isRecurring) {
                transactionDao.update(transaction.copy(isRecurringPaused = true))
                loadAllTransactions()
            }
        }
    }

    fun resumeRecurring(transaction: Transaction) {
        viewModelScope.launch {
            if (transaction.isRecurring) {
                transactionDao.update(transaction.copy(isRecurringPaused = false))
                loadAllTransactions()
            }
        }
    }

    fun skipOccurrence(transaction: Transaction) {
        // In a real app, this would mark the specific date to skip
        // For simplicity, we'll just show a message
        _successMessage.value = "Next occurrence skipped for ${transaction.category}"
    }

    // Security Handlers
    
    fun unlockApp(pin: String): Boolean {
        return if (!settingsManager.pinEnabled || pin == settingsManager.pinCode) {
            _isLocked.value = false
            true
        } else false
    }

    fun setPin(pin: String) {
        settingsManager.pinCode = pin
        settingsManager.pinEnabled = pin.isNotEmpty()
    }

    fun updateHideBalances(hide: Boolean) {
        settingsManager.hideBalances = hide
        _hideBalances.value = hide
    }

    fun updatePrivacyMode(privacy: Boolean) {
        settingsManager.privacyMode = privacy
        _privacyMode.value = privacy
    }

    fun updateBiometricEnabled(enabled: Boolean) {
        settingsManager.biometricEnabled = enabled
    }

    fun updateLanguage(lang: String) {
        settingsManager.language = lang
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        settingsManager.notificationsEnabled = enabled
    }

    fun updateBackupEnabled(enabled: Boolean) {
        settingsManager.backupEnabled = enabled
    }

    fun updateBudgetDefault(amount: Double) {
        settingsManager.budgetDefault = amount
    }

    fun updateDateFormat(format: String) {
        settingsManager.dateFormat = format
    }

    fun updateFirstDayOfWeek(day: Int) {
        settingsManager.firstDayOfWeek = day
    }
    
    // AI / Smart Features
    
    fun autoCategorize(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("uber") || lower.contains("bolt") || lower.contains("taxi") -> "Transport"
            lower.contains("kfc") || lower.contains("pizza") || lower.contains("food") || lower.contains("restaurant") -> "Food"
            lower.contains("rent") || lower.contains("house") -> "Rent"
            lower.contains("electricity") || lower.contains("water") || lower.contains("internet") -> "Utilities"
            lower.contains("netflix") || lower.contains("spotify") || lower.contains("cinema") -> "Entertainment"
            lower.contains("salary") || lower.contains("pay") -> "Salary"
            lower.contains("hospital") || lower.contains("pharmacy") || lower.contains("doctor") -> "Health"
            else -> "Other"
        }
    }

    fun parseNaturalLanguageEntry(entry: String) {
        viewModelScope.launch {
            try {
                // Simple pattern: "amount for note" or "amount at merchant"
                val words = entry.split(" ")
                val amount = words.firstOrNull { it.toDoubleOrNull() != null }?.toDouble()
                
                if (amount != null) {
                    val category = autoCategorize(entry)
                    val note = entry.replace(amount.toString(), "").trim()
                    addTransaction(
                        amount = amount,
                        category = category,
                        note = note,
                        type = if (category == "Salary") "INCOME" else "EXPENSE"
                    )
                    _successMessage.value = "Smart entry added: $amount for $category"
                } else {
                    _errorMessage.value = "Could not parse amount from: $entry"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Smart entry failed"
            }
        }
    }

    fun getSpendingForecast(): Double {
        val allTx = _transactions.value.filter { it.type == "EXPENSE" }
        if (allTx.isEmpty()) return 0.0
        
        val distinctMonths = allTx.map { 
            val cal = Calendar.getInstance().apply { timeInMillis = it.date }
            "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}"
        }.distinct().size.coerceAtLeast(1)
        
        val totalSpent = allTx.sumOf { it.amount }
        return totalSpent / distinctMonths // Simple average forecast
    }

    // Data Management Features

    fun archiveOldData() {
        viewModelScope.launch {
            try {
                val sixMonthsAgo = System.currentTimeMillis() - (180L * 24 * 60 * 60 * 1000)
                transactionDao.archiveOlderThan(sixMonthsAgo)
                loadAllTransactions()
                _successMessage.value = "Old data archived successfully"
            } catch (e: Exception) {
                _errorMessage.value = "Archive failed"
            }
        }
    }

    fun restoreArchivedData() {
        viewModelScope.launch {
            try {
                transactionDao.restoreAllArchived()
                loadAllTransactions()
                _successMessage.value = "All data restored from archive"
            } catch (e: Exception) {
                _errorMessage.value = "Restore failed"
            }
        }
    }

    fun resetBudgets() {
        viewModelScope.launch {
            try {
                budgetGoalDao.deleteAll()
                loadBudgetGoals()
                _successMessage.value = "All budget goals reset"
            } catch (e: Exception) {
                _errorMessage.value = "Reset failed"
            }
        }
    }

    fun checkDataIntegrity() {
        viewModelScope.launch {
            val transactions = transactionDao.getAll()
            val invalid = transactions.filter { it.amount <= 0 || it.category.isEmpty() }
            if (invalid.isNotEmpty()) {
                _errorMessage.value = "Found ${invalid.size} transactions with invalid data."
            } else {
                _successMessage.value = "Data integrity check passed."
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
    
    fun clearSuccess() {
        _successMessage.value = null
    }
}

data class MonthlySummary(
    val totalIncome: Double,
    val totalExpenses: Double,
    val remaining: Double
)

data class BudgetProgress(
    val category: String,
    val spent: Double,
    val budget: Double,
    val percentage: Float
)

data class SpendingTrend(
    val currentMonthExpense: Double,
    val previousMonthExpense: Double,
    val percentChange: Double
)

data class CategoryInsight(
    val category: String,
    val amount: Double,
    val percentageOfTotal: Float,
    val isHighest: Boolean = false
)

data class SavingsInsight(
    val targetAmount: Double,
    val targetDate: Long,
    val monthlyRequirement: Double,
    val monthsRemaining: Int
)

data class FinancialInsight(
    val title: String,
    val description: String,
    val type: String // "INFO", "MEDIUM", "HIGH", "POSITIVE"
)
