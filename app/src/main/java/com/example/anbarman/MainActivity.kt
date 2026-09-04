package com.example.anbarman

import android.app.AlarmManager
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private val prefs by lazy {
        getSharedPreferences("store", MODE_PRIVATE)
    }

    private val dateFormat =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            isLenient = false
        }

    private lateinit var list: LinearLayout
    private lateinit var search: EditText

    private var pendingBarcode: EditText? = null
    private var filter = "all"

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        list = findViewById(R.id.list)
        search = findViewById(R.id.search)

        findViewById<Button>(R.id.addBtn).setOnClickListener {
            showProductDialog(null)
        }

        findViewById<Button>(R.id.settingsBtn).setOnClickListener {
            showSettings()
        }

        search.addTextChangedListener(object : android.text.TextWatcher {

            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                render()
            }

            override fun afterTextChanged(
                s: android.text.Editable?
            ) = Unit
        })

        findViewById<Button>(R.id.allBtn).setOnClickListener {
            filter = "all"
            render()
        }

        findViewById<Button>(R.id.nearBtn).setOnClickListener {
            filter = "near"
            render()
        }

        findViewById<Button>(R.id.expiredBtn).setOnClickListener {
            filter = "expired"
            render()
        }

        requestNotificationPermission()
        render()
    }

    private fun data(): JSONArray {
        return try {
            JSONArray(prefs.getString("items", "[]"))
        } catch (_: Exception) {
            JSONArray()
        }
    }

    private fun save(items: JSONArray) {
        prefs.edit()
            .putString("items", items.toString())
            .apply()
    }

    private fun requestNotificationPermission() {
        if (
            android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf("android.permission.POST_NOTIFICATIONS"),
                20
            )
        }
    }

    private data class ProductStatus(
        val total: Int,
        val status: Int,
        val nearest: Long
    )

    private fun statusFor(product: JSONObject): ProductStatus {

        var total = 0
        var status = 0
        var nearest = Long.MAX_VALUE

        val now = System.currentTimeMillis()
        val nearWindow = 7L * 86_400_000L

        val batches =
            product.optJSONArray("batches") ?: JSONArray()

        for (j in 0 until batches.length()) {

            val batch =
                batches.optJSONObject(j) ?: continue

            total += batch
                .optInt("qty", 0)
                .coerceAtLeast(0)

            val date =
                parseDate(batch.optString("expiry"))
                    ?: continue

            nearest = minOf(nearest, date)

            status = when {
                date < now -> maxOf(status, 2)
                date - now <= nearWindow -> maxOf(status, 1)
                else -> status
            }
        }

        return ProductStatus(
            total,
            status,
            nearest
        )
    }

    private fun render() {

        list.removeAllViews()

        val items = data()

        var expired = 0
        var near = 0

        val q = search.text.toString().trim()

        val visible =
            mutableListOf<Pair<Int, ProductStatus>>()

        for (i in 0 until items.length()) {

            val product =
                items.optJSONObject(i) ?: continue

            val status =
                statusFor(product)

            if (status.status == 2) {
                expired++
            } else if (status.status == 1) {
                near++
            }

            val matches =
                q.isEmpty() ||
                    listOf(
                        product.optString("name"),
                        product.optString("barcode"),
                        product.optString("category")
                    ).any {
                        it.contains(q, ignoreCase = true)
                    }

            val filterMatches =
                when (filter) {
                    "near" -> status.status == 1
                    "expired" -> status.status == 2
                    else -> true
                }

            if (matches && filterMatches) {
                visible += i to status
            }
        }

        visible.sortBy {
            it.second.nearest
        }

        for ((index, status) in visible) {

            val product =
                items.getJSONObject(index)

            val card =
                TextView(this).apply {

                    setPadding(
                        20,
                        18,
                        20,
                        18
                    )

                    textSize = 17f
                    gravity = Gravity.CENTER_VERTICAL

                    val icon =
                        when (status.status) {
                            2 -> "🔴"
                            1 -> "🟠"
                            else -> "🟢"
                        }

                    text =
                        "$icon ${product.optString("name", "بدون نام")}\n" +
                        "موجودی کل: ${status.total}   |   " +
                        "${product.optString("category", "بدون دسته‌بندی")}"

                    setOnClickListener {
                        showProductDialog(index)
                    }
                }

            val bg =
                when (status.status) {
                    2 -> 0x22FF0000
                    1 -> 0x22FF8800
                    else -> 0x11000000
                }

            card.setBackgroundColor(bg)

            list.addView(
                card,
                LinearLayout.LayoutParams(
                    -1,
                    -2
                ).apply {
                    setMargins(
                        0,
                        0,
                        0,
                        10
                    )
                }
            )
        }

        findViewById<TextView>(R.id.expiredCount).text =
            "🔴 $expired منقضی"

        findViewById<TextView>(R.id.nearCount).text =
            "🟠 $near نزدیک"
    }

    private fun showProductDialog(index: Int?) {

        val items = data()

        val product =
            if (index != null) {
                items.getJSONObject(index)
            } else {
                JSONObject()
            }

        val box =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(30, 0, 30, 0)
            }

        fun edit(
            hint: String,
            value: String = "",
            type: Int = InputType.TYPE_CLASS_TEXT
        ): EditText {
            return EditText(this).apply {
                this.hint = hint
                setText(value)
                inputType = type
                setSingleLine()
            }
        }

        val name =
            edit(
                "اسم کالا *",
                product.optString("name")
            )

        val barcode =
            edit(
                "بارکد",
                product.optString("barcode"),
                InputType.TYPE_CLASS_NUMBER
            )

        val category =
            edit(
                "دسته‌بندی",
                product.optString("category")
            )

        val price =
            edit(
                "قیمت",
                product.optString("price"),
                InputType.TYPE_CLASS_NUMBER
            )

        val description =
            EditText(this).apply {
                hint = "توضیحات"
                setText(product.optString("desc"))
                minLines = 2
            }

        listOf(
            name,
            barcode,
            category,
            price,
            description
        ).forEach(box::addView)

        pendingBarcode = barcode

        Button(this).apply {

            text = "📷 اسکن بارکد"

            setOnClickListener {

                startActivityForResult(
                    Intent(
                        this@MainActivity,
                        ScannerActivity::class.java
                    ),
                    44
                )
            }

        }.also(box::addView)

        TextView(this).apply {

            text = "سری‌ها / محموله‌ها"
            textSize = 18f

            setPadding(
                0,
                18,
                0,
                6
            )

        }.also(box::addView)

        val batchesContainer =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

        box.addView(batchesContainer)

        fun refreshBatches() {

            batchesContainer.removeAllViews()

            val batches =
                product.optJSONArray("batches")
                    ?: JSONArray().also {
                        product.put("batches", it)
                    }

            if (batches.length() == 0) {

                TextView(this).apply {

                    text = "هنوز سری‌ای اضافه نشده"

                    setPadding(
                        0,
                        8,
                        0,
                        8
                    )

                }.also(
                    batchesContainer::addView
                )
            }

            for (j in 0 until batches.length()) {

                val batch =
                    batches.getJSONObject(j)

                val view =
                    TextView(this).apply {

                        setPadding(
                            0,
                            12,
                            0,
                            12
                        )

                        text =
                            "سری ${j + 1}: ${batch.optInt("qty")} عدد\n" +
                            "تولید: ${batch.optString("production", "-")}   |   " +
                            "انقضا: ${batch.optString("expiry", "-")}"

                        setOnClickListener {
                            showBatchDialog(
                                product,
                                j,
                                ::refreshBatches
                            )
                        }
                    }

                batchesContainer.addView(view)
            }
        }

        refreshBatches()

        Button(this).apply {

            text = "＋ افزودن سری / بار جدید"

            setOnClickListener {
                showBatchDialog(
                    product,
                    null,
                    ::refreshBatches
                )
            }

        }.also(box::addView)

        val dialog =
            AlertDialog.Builder(this)
                .setTitle(
                    if (index == null)
                        "افزودن کالا"
                    else
                        "ویرایش کالا"
                )
                .setView(box)
                .setPositiveButton(
                    "ذخیره",
                    null
                )
                .setNegativeButton(
                    "انصراف",
                    null
                )
                .create()

        if (index != null) {

            dialog.setButton(
                AlertDialog.BUTTON_NEUTRAL,
                "حذف کالا",
                DialogInterface.OnClickListener { _, _ ->

                    items.remove(index)
                    save(items)
                    cancelProductAlarms(product)

                    dialog.dismiss()
                    render()
                }
            )
        }

        dialog.setOnShowListener {

            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {

                if (
                    name.text.toString()
                        .trim()
                        .isEmpty()
                ) {

                    name.error =
                        "نام کالا را وارد کنید"

                    return@setOnClickListener
                }

                product.put(
                    "name",
                    name.text.toString().trim()
                )

                product.put(
                    "barcode",
                    barcode.text.toString().trim()
                )

                product.put(
                    "category",
                    category.text.toString().trim()
                )

                product.put(
                    "price",
                    price.text.toString().trim()
                )

                product.put(
                    "desc",
                    description.text.toString().trim()
                )

                if (!product.has("batches")) {
                    product.put(
                        "batches",
                        JSONArray()
                    )
                }

                if (index == null) {
                    items.put(product)
                }

                save(items)
                rescheduleAll(product)

                dialog.dismiss()
                render()
            }
        }

        dialog.show()
    }

    private fun showBatchDialog(
        product: JSONObject,
        index: Int?,
        refresh: () -> Unit
    ) {

        val batches =
            product.optJSONArray("batches")
                ?: JSONArray().also {
                    product.put("batches", it)
                }

        val old =
            if (index != null) {
                batches.getJSONObject(index)
            } else {
                JSONObject()
            }

        val box =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    24,
                    0,
                    24,
                    0
                )
            }

        val qty =
            EditText(this).apply {

                hint = "تعداد *"

                inputType =
                    InputType.TYPE_CLASS_NUMBER

                setText(
                    if (old.has("qty")) {
                        old.optInt("qty").toString()
                    } else {
                        ""
                    }
                )
            }

        box.addView(qty)

        fun dateField(
            title: String,
            key: String,
            required: Boolean
        ): EditText {

            val field =
                EditText(this).apply {

                    hint =
                        title +
                            if (required) {
                                " *"
                            } else {
                                ""
                            }

                    isFocusable = false

                    setText(
                        old.optString(key)
                    )

                    setOnClickListener {

                        val cal =
                            Calendar.getInstance()

                        DatePickerDialog(
                            this@MainActivity,
                            { _, y, m, d ->

                                setText(
                                    String.format(
                                        Locale.US,
                                        "%04d-%02d-%02d",
                                        y,
                                        m + 1,
                                        d
                                    )
                                )
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }
                }

            box.addView(field)

            return field
        }

        val production =
            dateField(
                "تاریخ تولید",
                "production",
                false
            )

        val expiry =
            dateField(
                "تاریخ انقضا",
                "expiry",
                true
            )

        val alarm =
            EditText(this).apply {

                hint =
                    "چند روز قبل اعلان بدهد؟"

                inputType =
                    InputType.TYPE_CLASS_NUMBER

                setText(
                    old.optInt(
                        "alarm",
                        7
                    ).toString()
                )
            }

        box.addView(alarm)

        val dialog =
            AlertDialog.Builder(this)
                .setTitle(
                    if (index == null)
                        "سری جدید"
                    else
                        "ویرایش سری"
                )
                .setView(box)
                .setPositiveButton(
                    "ذخیره",
                    null
                )
                .setNegativeButton(
                    "انصراف",
                    null
                )
                .create()

        if (index != null) {

            dialog.setButton(
                AlertDialog.BUTTON_NEUTRAL,
                "حذف سری",
                DialogInterface.OnClickListener { _, _ ->

                    batches.remove(index)

                    product.put(
                        "batches",
                        batches
                    )

                    refresh()

                    dialog.dismiss()
                }
            )
        }

        dialog.setOnShowListener {

            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {

                val q =
                    qty.text.toString()
                        .toIntOrNull()

                val exp =
                    expiry.text.toString().trim()

                if (q == null || q < 0) {

                    qty.error =
                        "تعداد معتبر وارد کنید"

                    return@setOnClickListener
                }

                if (parseDate(exp) == null) {

                    expiry.error =
                        "تاریخ انقضا را انتخاب کنید"

                    return@setOnClickListener
                }

                if (
                    production.text.isNotBlank() &&
                    parseDate(
                        production.text.toString()
                    ) == null
                ) {

                    production.error =
                        "تاریخ نامعتبر"

                    return@setOnClickListener
                }

                val days =
                    alarm.text.toString()
                        .toIntOrNull()
                        ?.coerceAtLeast(0)
                        ?: 7

                val batch =
                    JSONObject()
                        .put(
                            "id",
                            old.optString(
                                "id",
                                UUID.randomUUID().toString()
                            )
                        )
                        .put(
                            "qty",
                            q
                        )
                        .put(
                            "production",
                            production.text.toString()
                        )
                        .put(
                            "expiry",
                            exp
                        )
                        .put(
                            "alarm",
                            days
                        )

                if (index == null) {
                    batches.put(batch)
                } else {
                    batches.put(
                        index,
                        batch
                    )
                }

                product.put(
                    "batches",
                    batches
                )

                refresh()

                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun parseDate(
        value: String
    ): Long? {

        return try {
            dateFormat.parse(value)?.time
        } catch (_: Exception) {
            null
        }
    }

    private fun alarmManager(): AlarmManager {
        return getSystemService(
            ALARM_SERVICE
        ) as AlarmManager
    }

    private fun requestCode(
        product: JSONObject,
        batch: JSONObject
    ): Int {

        return (
            product.optString("barcode") +
                "|" +
                product.optString("name") +
                "|" +
                batch.optString(
                    "id",
                    batch.optString("expiry")
                )
            ).hashCode()
    }

    private fun scheduleDate(
        product: JSONObject,
        batch: JSONObject
    ) {

        val time =
            parseDate(
                batch.optString("expiry")
            ) ?: return

        val days =
            batch.optInt(
                "alarm",
                7
            ).coerceAtLeast(0)

        val trigger =
            time -
                days * 86_400_000L

        if (
            trigger <=
            System.currentTimeMillis()
        ) {
            return
        }

        val intent =
            Intent(
                this,
                ExpiryReceiver::class.java
            ).apply {

                putExtra(
                    "name",
                    product.optString(
                        "name",
                        "کالا"
                    )
                )

                putExtra(
                    "date",
                    batch.optString(
                        "expiry"
                    )
                )

                putExtra(
                    "days",
                    days
                )
            }

        val pending =
            PendingIntent.getBroadcast(
                this,
                requestCode(
                    product,
                    batch
                ),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        alarmManager().set(
            AlarmManager.RTC_WAKEUP,
            trigger,
            pending
        )
    }

    private fun rescheduleAll(
        product: JSONObject
    ) {

        cancelProductAlarms(product)

        val batches =
            product.optJSONArray("batches")
                ?: return

        for (i in 0 until batches.length()) {

            scheduleDate(
                product,
                batches.getJSONObject(i)
            )
        }
    }

    private fun cancelProductAlarms(
        product: JSONObject
    ) {

        val batches =
            product.optJSONArray("batches")
                ?: return

        for (i in 0 until batches.length()) {

            val batch =
                batches.getJSONObject(i)

            val intent =
                Intent(
                    this,
                    ExpiryReceiver::class.java
                )

            val pending =
                PendingIntent.getBroadcast(
                    this,
                    requestCode(
                        product,
                        batch
                    ),
                    intent,
                    PendingIntent.FLAG_NO_CREATE or
                        PendingIntent.FLAG_IMMUTABLE
                )

            if (pending != null) {
                alarmManager().cancel(pending)
            }
        }
    }

    private fun showSettings() {

        val options =
            arrayOf(
                "سیستم",
                "سفید",
                "مشکی"
            )

        val current =
            prefs.getString(
                "theme",
                "سیستم"
            ) ?: "سیستم"

        AlertDialog.Builder(this)
            .setTitle("تم برنامه")
            .setSingleChoiceItems(
                options,
                options.indexOf(current)
                    .coerceAtLeast(0)
            ) { dialog, which ->

                prefs.edit()
                    .putString(
                        "theme",
                        options[which]
                    )
                    .apply()

                dialog.dismiss()

                applyTheme()

                recreate()
            }
            .show()
    }

    private fun applyTheme() {

        when (
            prefs.getString(
                "theme",
                "سیستم"
            )
        ) {

            "مشکی" ->
                AppCompatDelegate.setDefaultNightMode(
                    AppCompatDelegate.MODE_NIGHT_YES
                )

            "سفید" ->
                AppCompatDelegate.setDefaultNightMode(
                    AppCompatDelegate.MODE_NIGHT_NO
                )

            else ->
                AppCompatDelegate.setDefaultNightMode(
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                )
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {

        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (
            requestCode == 44 &&
            resultCode == RESULT_OK
        ) {

            data?.getStringExtra(
                "barcode"
            )?.let {

                pendingBarcode?.setText(it)
            }
        }
    }
}
