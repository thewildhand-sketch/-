package com.example.model

/**
 * Exact 51-column schema mirroring the user's attached Excel template (blank_4d62f47c.txt).
 * Header names, spelling, trailing spaces, and duplicate "due date" columns are preserved verbatim.
 */
object JournalSchema {
    val EXACT_HEADERS: List<String> = listOf(
        "رقم العملية",                              // 0
        "Main account name",                        // 1
        "Sub account name",                         // 2
        "Trans. Name",                              // 3
        "cust. code",                               // 4
        "customer name",                            // 5
        "supp. code",                               // 6
        "Suppliar name",                            // 7
        "H",                                        // 8
        "payment invoice num.",                     // 9
        "كود الموظف",                               // 10
        "اسم الموظف",                               // 11
        "بيان",                                     // 12
        "shift num.",                               // 13
        "debit",                                    // 14
        "credit",                                   // 15
        "balanc",                                   // 16
        "condition",                                // 17
        "date",                                     // 18
        "Month",                                    // 19
        "Payment cheque num.",                      // 20
        "due date",                                 // 21 (Payment cheque due date)
        "the supplier code of Payment Cheque",      // 22
        "the supplier name of Payment Cheque",      // 23
        "sales tax2372",                            // 24
        "customer's tax1357",                       // 25
        "Received cheque num.",                     // 26
        "due date",                                 // 27 (Received cheque due date)
        "the customer code of received cheque",     // 28
        "the customer name of received cheque",     // 29
        "خصم ضرائب ا ت ص 1%2361",                   // 30
        "شهر استحقاقات اوراق الدفع",                // 31
        "تورايخ أ . د",                              // 32
        "الشهر 2",                                  // 33
        "سنة اوراق الدفع ",                         // 34
        "المرتجعات سليمة",                          // 35
        "تالفة",                                    // 36
        "الخصومات",                                 // 37
        "الضرائب",                                  // 38
        "ملاحظات",                                  // 39
        "عدد الفيزا",                               // 40
        "عجز / زيادة",                              // 41
        "شهر فواتير المشتريات",                     // 42
        "رقم شيك العملاء",                          // 43
        "تاريخ الشيك الوارد",                       // 44
        "العميل",                                   // 45
        "رقم الفيزا",                               // 46
        "كود الفرع",                                // 47
        "اسم الفرع",                                // 48
        "تصنيف المورد",                             // 49
        "البنك"                                     // 50
    )

    val NUMERIC_COLUMN_INDICES: Set<Int> = setOf(14, 15, 16, 24, 25, 30, 35, 36, 37, 38, 40, 41)

    val HEADER_LABELS_AR: List<String> = listOf(
        "رقم العملية",
        "الحساب الرئيسي (Main account name)",
        "الحساب الفرعي (Sub account name)",
        "نوع المعاملة (Trans. Name)",
        "كود العميل (cust. code)",
        "اسم العميل (customer name)",
        "كود المورد (supp. code)",
        "اسم المورد (Suppliar name)",
        "H",
        "رقم فاتورة الدفع",
        "كود الموظف",
        "اسم الموظف",
        "البيان",
        "رقم الشيفت (shift num.)",
        "مدين (debit)",
        "دائن (credit)",
        "الفرق/الرصيد (balanc)",
        "الحالة (condition)",
        "التاريخ (date)",
        "الشهر (Month)",
        "رقم شيك الدفع",
        "تاريخ استحقاق شيك الدفع",
        "كود مورد شيك الدفع",
        "اسم مورد شيك الدفع",
        "ضريبة مبيعات 2372",
        "ضريبة عملاء 1357",
        "رقم الشيك الوارد",
        "تاريخ استحقاق الشيك الوارد",
        "كود عميل الشيك الوارد",
        "اسم عميل الشيك الوارد",
        "خصم ضرائب أ ت ص 1% 2361",
        "شهر استحقاقات أوراق الدفع",
        "تواريخ أ . د",
        "الشهر 2",
        "سنة أوراق الدفع",
        "المرتجعات سليمة",
        "تالفة",
        "الخصومات",
        "الضرائب",
        "ملاحظات",
        "عدد الفيزا",
        "عجز / زيادة",
        "شهر فواتير المشتريات",
        "رقم شيك العملاء",
        "تاريخ الشيك الوارد",
        "العميل",
        "رقم الفيزا",
        "كود الفرع",
        "اسم الفرع",
        "تصنيف المورد",
        "البنك"
    )
}

enum class MasterEntityType(val titleAr: String) {
    SUPPLIER("الموردين"),
    CUSTOMER("العملاء"),
    MAIN_ACCOUNT("الحسابات الرئيسية"),
    SUB_ACCOUNT("الحسابات الفرعية"),
    TRANS_NAME("أسماء المعاملات"),
    EMPLOYEE("الموظفين"),
    BRANCH("الفروع"),
    BANK("البنوك"),
}

enum class JournalSourceType(val labelAr: String) {
    INVOICE_PHOTO("صورة فاتورة"),
    SALES_SHIFT("شيفت مبيعات"),
    PDF_BATCH("ملف PDF مجمّع"),
    EXCEL_IMPORT("استيراد إكسيل/ملف"),
    MANUAL("إدخال يدوي"),
}

enum class EntryTemplateKind(val labelAr: String) {
    PURCHASE_INVOICE("فاتورة مشتريات"),
    SALES_SHIFT("شيفت مبيعات"),
    PAYMENT_CHEQUE("شيك دفع (مورد)"),
    RECEIVED_CHEQUE("شيك قبض (عميل)"),
    RETURN_OR_DISCOUNT("مرتجع / خصم"),
    GENERAL_ENTRY("قيد يومية عام"),
}
