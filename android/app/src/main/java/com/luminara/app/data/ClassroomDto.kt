package com.luminara.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Accounts and classes. The lecture types are unchanged. */

@Serializable
data class UserDto(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "student",     // student | teacher
    val language: String = "en",
) {
    val isTeacher get() = role == "teacher"
}

@Serializable
data class AuthResponseDto(
    val token: String = "",
    val user: UserDto = UserDto(),
)

@Serializable
data class MeDto(val user: UserDto = UserDto())

@Serializable
data class ClassDto(
    val id: String = "",
    val name: String = "",
    val subject: String = "",
    @SerialName("join_code") val joinCode: String = "",
    @SerialName("teacher_id") val teacherId: String = "",
    @SerialName("teacher_name") val teacherName: String = "",
    @SerialName("student_count") val studentCount: Int = 0,
    @SerialName("lecture_count") val lectureCount: Int = 0,
    @SerialName("published_count") val publishedCount: Int = 0,
    @SerialName("is_teacher") val isTeacher: Boolean = false,
)

@Serializable
data class ClassListDto(val classes: List<ClassDto> = emptyList())

@Serializable
data class CreateClassDto(@SerialName("class") val schoolClass: ClassDto = ClassDto())

@Serializable
data class JoinClassDto(
    @SerialName("class") val schoolClass: ClassDto = ClassDto(),
    @SerialName("already_member") val alreadyMember: Boolean = false,
)

@Serializable
data class ClassLectureDto(
    val id: String = "",
    val title: String = "",
    val status: String = "created",
    val published: Boolean = false,
    val language: String = "en",
    @SerialName("duration_sec") val durationSec: Double = 0.0,
    val engine: String = "",
    @SerialName("source_type") val sourceType: String = "upload",
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class ClassDetailDto(
    @SerialName("class") val schoolClass: ClassDto = ClassDto(),
    val lectures: List<ClassLectureDto> = emptyList(),
)

@Serializable
data class PublishDto(
    @SerialName("lecture_id") val lectureId: String = "",
    val published: Boolean = false,
)

@Serializable
data class UploadResponseDto(
    @SerialName("lecture_id") val lectureId: String = "",
    val status: String = "created",
    @SerialName("class_id") val classId: String? = null,
    val published: Boolean = false,
)
