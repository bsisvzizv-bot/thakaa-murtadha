package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Healing
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class PromptCategory(
    val title: String,
    val icon: ImageVector,
    val questions: List<String>
)

val SamplePromptCategories = listOf(
    PromptCategory(
        title = "استشارات طبية 🩺",
        icon = Icons.Rounded.MedicalServices,
        questions = listOf(
            "ما هي أسباب الصداع المستمر وكيف أميز بين الشقيقة والجيوب الأنفية؟",
            "ما هي أعراض نقص فيتامين د وفيتامين ب 12 وطرق العلاج؟",
            "كيف أتعامل مع ارتفاع ضغط الدم المفاجئ وما هي خطوات السيطرة عليه؟",
            "ما هي أعراض القولون العصبي وأفضل حمية غذائية للتخلص من الغازات؟",
            "كيف أميز بين ألم المعدة والحصوات المرارية والزائدة الدودية؟",
            "أسباب خفقان القلب وتسارع النبض المفاجئ ومتى يستوجب الفحص الطبي؟",
            "ما هي أعراض مقاومة الإنسولين وكيفية الوقاية من مرض السكري؟",
            "طرق علاج ارتجاع المريء وحموضة المعدة بدون أدوية"
        )
    ),
    PromptCategory(
        title = "أدوية وإسعافات 💊",
        icon = Icons.Rounded.Healing,
        questions = listOf(
            "ما هي خطوات الإسعافات الأولية الصحيحة للحروق والجروح والنزيف؟",
            "كيف أتصرف فوراً عند حدوث انسداد مجرى الهواء أو الغصة للكبار والأطفال؟",
            "ما الفرق بين المسكنات (باراسيتامول، إيبوبروفين، أسبرين) ومتى يحظر استخدامها؟",
            "كيف أتعامل مع حالات التسمم الغذائي والجفاف عند الكبار والأطفال؟",
            "طريقة إسعاف مريض السكري عند حدوث هبوط السكر الحاد أو الإغماء",
            "أفضل الإجراءات لعلاج الحساسية الجلدية المفاجئة والطفح الجلدي"
        )
    ),
    PromptCategory(
        title = "تغذية ومناعة 🥗",
        icon = Icons.Rounded.Restaurant,
        questions = listOf(
            "ما هو أفضل نظام غذائي لتقوية جهاز المناعة ومقاومة الأمراض؟",
            "كيف أنظم وجباتي لخفض الكوليسترول الضار والدهون الثلاثية بطرق طبيعية؟",
            "ما هي الأطعمة الفعالة لتنقية الكبد من الدهون وتحسين وظائفه؟",
            "خطة غذائية متكاملة لزيادة مخزون الحديد وعلاج فقر الدم بسرعة",
            "ما هي الكمية الموصى بها من الماء والبروتين التي يحتاجها الجسم يومياً؟",
            "أفضل النصائح الطبية للحفاظ على صحة الكلى ومنع تشكل الحصوات"
        )
    ),
    PromptCategory(
        title = "صحة نفسية ونوم 🧠",
        icon = Icons.Rounded.Psychology,
        questions = listOf(
            "طرق علمية مثبتة للتخلص من نوبات الهلع والقلق المزمن",
            "كيف أتغلب على الأرق وصعوبة النوم ليلاً بدون مهدئات؟",
            "ما هي علامات الاحتراق النفسي والتوتر العصبي وطرق التعافي؟",
            "تمارين تنفس واسترخاء لتهدئة ضربات القلب وتخفيف الضغط العصبي"
        )
    ),
    PromptCategory(
        title = "برمجة وتقنية 💻",
        icon = Icons.Rounded.Code,
        questions = listOf(
            "كيف أبدأ رحلة تعلم البرمجة والذكاء الاصطناعي من الصفر؟",
            "ما الفرق بين واجهات API، قواعد البيانات، والحوسبة السحابية؟",
            "اكتب لي كود بايثون لمعالجة وتحليل البيانات وتصورها بيانياً",
            "أفضل خارطة طريق لاحتراف الأمن السيبراني واختبار الاختراق الأخلاقي"
        )
    ),
    PromptCategory(
        title = "كتابة وتلخيص ✍️",
        icon = Icons.Rounded.EditNote,
        questions = listOf(
            "اكتب لي سيرة ذاتية (CV) احترافية ورسالة تغطية للتقديم على وظيفة",
            "لخص لي أهم مهارات الذكاء العاطفي والتواصل الفعال في نقاط",
            "اكتب رسالة شكر واعتذار رسمية لجهة العمل بأسلوب راقٍ",
            "اقترح 5 أفكار مشاريع تقنية مبتكرة ذات جدوى اقتصادية عالية"
        )
    ),
    PromptCategory(
        title = "دراسة وعلوم 📚",
        icon = Icons.Rounded.School,
        questions = listOf(
            "كيف أضع جدولاً دراسياً فعالاً لأسبوع الامتحانات وأتخلص من التشتت؟",
            "اشرح نظرية النسبية لأينشتاين ومفهوم الثقوب السوداء ببساطة",
            "ما هي أفضل تقنيات الحفظ السريع وتقوية الذاكرة الطويلة المدى؟"
        )
    ),
    PromptCategory(
        title = "تطوير الذات والمال 💼",
        icon = Icons.AutoMirrored.Rounded.TrendingUp,
        questions = listOf(
            "كيف أتخلص من عادة التسويف والتأجيل وأضاعف تركيزي؟",
            "ما هي أهم قواعد الإدارة المالية الشخصية والاستثمار الذكي؟",
            "كيف أبني عادات يومية إيجابية تدوم مدى الحياة؟"
        )
    )
)

@Composable
fun SuggestedPromptsSection(
    onSelectQuestion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategoryIndex by remember { mutableStateOf(0) }
    val currentCategory = SamplePromptCategories.getOrElse(selectedCategoryIndex) { SamplePromptCategories[0] }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 6.dp)
            )
            Text(
                text = "موسوعة الأسئلة المقترحة والاستشارات الطبية",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Categories tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SamplePromptCategories.forEachIndexed { index, category ->
                val isSelected = selectedCategoryIndex == index
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedCategoryIndex = index },
                    label = { Text(category.title, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        // Prompt question cards for the current category
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            currentCategory.questions.forEach { question ->
                Surface(
                    onClick = { onSelectQuestion(question) },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = question,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "اسأل",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
