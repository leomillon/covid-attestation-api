package com.github.leomillon.covidattestationapi

import com.google.zxing.EncodeHintType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.glxn.qrgen.javase.QRCode
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.springframework.core.io.InputStreamResource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.awt.Color
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.validation.Valid
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotEmpty

private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE)
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.FRANCE)

@RestController
class CovidController {

  @PostMapping(
    "/api/v1/docs/generate",
    consumes = [MediaType.APPLICATION_JSON_VALUE],
    produces = [MediaType.APPLICATION_PDF_VALUE]
  )
  suspend fun generateDoc(@Valid @RequestBody input: GenerateDocInputDto): ResponseEntity<InputStreamResource> {
    val firstname = input.firstname
    val lastname = input.lastname
    val birthDate = input.birthDate
    val birthDateFormatted = birthDate.format(dateFormatter)
    val birthPlace = input.birthPlace
    val city = input.city
    val postalCode = input.postalCode
    val address = input.address
    val fullAddress = listOfNotNull(address, postalCode, city).joinToString(" ")
    val exitDateTime = input.exitDateTime.toLocalDateTime()
    val fullname = listOfNotNull(firstname, lastname).joinToString(" ")
    val exitDateFormatted = exitDateTime.format(dateFormatter)
    val exitTimeFormatted = exitDateTime.format(timeFormatter)
    val reasons = input.reasons
    val now = LocalDateTime.now()
    val qrCodeContent = """
      Cree le: ${now.format(dateFormatter)} a ${now.format(DateTimeFormatter.ofPattern("HH'h'mm", Locale.FRANCE))};
       Nom: $lastname;
       Prenom: $firstname;
       Naissance: $birthDateFormatted a $birthPlace;
       Adresse: $fullAddress;
       Sortie: $exitDateFormatted a $exitTimeFormatted;
       Motifs: ${reasons.joinToString(", ") { it.code }}
      """.trimIndent()

    val outputStream = ByteArrayOutputStream()

    withContext(Dispatchers.IO) {
      val doc = PDDocument.load(this::class.java.getResourceAsStream("/templates/attestation_empty.pdf"))
      val homePage = doc.pages.first()
      PDPageContentStream(doc, homePage, PDPageContentStream.AppendMode.APPEND, true)
        .apply {
          CovidPageWriter(doc, homePage, this).apply {
            writeFullname(fullname)
            writeBirthDate(birthDateFormatted)
            writeBirthPlace(birthPlace)
            writeAddress(fullAddress)
            writeSignCity(city)
            writeExitDate(exitDateFormatted)
            writeExitTime(exitTimeFormatted)

            reasons.forEach { checkReason(it) }

            writeSignQRCode(qrCodeContent)
          }
          close()
        }

      val secondPage = PDPage(homePage.mediaBox)
      doc.addPage(secondPage)
      PDPageContentStream(doc, secondPage, PDPageContentStream.AppendMode.APPEND, true)
        .apply {
          CovidPageWriter(doc, secondPage, this).apply {
            writeBigQRCode(qrCodeContent)
          }
          close()
        }
      doc.save(outputStream)
    }

    val filename = "attestation-${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.FRANCE))}_${now.format(DateTimeFormatter.ofPattern("HH-mm", Locale.FRANCE))}.pdf"

    return ResponseEntity
      .ok()
      .header("Content-Disposition", "inline; filename=$filename")
      .contentType(MediaType.APPLICATION_PDF)
      .body(InputStreamResource(ByteArrayInputStream(outputStream.toByteArray())))
  }
}

data class GenerateDocInputDto(
  @get:NotBlank
  val firstname: String,
  @get:NotBlank
  val lastname: String,
  val birthDate: LocalDate,
  @get:NotBlank
  val birthPlace: String,
  @get:NotBlank
  val city: String,
  @get:NotBlank
  val postalCode: String,
  @get:NotBlank
  val address: String,
  val exitDateTime: OffsetDateTime,
  @NotEmpty
  val reasons: Set<Reason>
)

enum class Reason(val code: String) {
  WORK("travail"),
  SHOPPING("achats"),
  HEALTH("sante"),
  FAMILY("famille"),
  DISABILITY("handicap"),
  SPORT_ANIMALS("sport_animaux"),
  CONVOCATION("convocation"),
  MISSIONS("missions"),
  CHILDREN("enfants")
}

class CovidPageWriter(
  private val document: PDDocument,
  private val page: PDPage,
  private val contentStream: PDPageContentStream
) {

  fun writeFullname(fullname: String) = writeText(fullname, 120f, 695f)
  fun writeBirthDate(birthDate: String) = writeText(birthDate, 120f, 673f)
  fun writeBirthPlace(birthPlace: String) = writeText(birthPlace, 300f, 673f)
  fun writeAddress(address: String) = writeText(address, 130f, 651f)
  fun writeSignCity(city: String) = writeText(city, 110f, 175f)
  fun writeExitDate(date: String) = writeText(date, 95f, 152f)
  fun writeExitTime(time: String) = writeText(time, 255f, 152f)

  fun displayGrid() {
    with(contentStream) {
      val width = page.mediaBox.width
      val height = page.mediaBox.height
      setStrokingColor(Color(255, 0, 0, 50))
      (0..width.toInt() step 10)
        .forEach { offsetX ->
          (0..height.toInt() step 10)
            .forEach { offsetY ->
              moveTo(offsetX.toFloat(), 0f)
              lineTo(offsetX.toFloat(), height)
              stroke()

              moveTo(0f, offsetY.toFloat())
              lineTo(width, offsetY.toFloat())
              stroke()
            }
        }

      setStrokingColor(Color.BLACK)
      (0..width.toInt() step 50)
        .forEach { offsetX ->
          (0..height.toInt() step 50)
            .forEach { offsetY ->
              moveTo(offsetX.toFloat(), 0f)
              lineTo(offsetX.toFloat(), height)
              stroke()

              moveTo(0f, offsetY.toFloat())
              lineTo(width, offsetY.toFloat())
              stroke()
            }
        }
    }
  }

  fun checkReason(reason: Reason) {
    when (reason) {
      Reason.WORK -> writeCheckBox(77f, 577f)
      Reason.SHOPPING -> writeCheckBox(77f, 532f)
      Reason.HEALTH -> writeCheckBox(77f, 476f)
      Reason.FAMILY -> writeCheckBox(77f, 435f)
      Reason.DISABILITY -> writeCheckBox(77f, 395f)
      Reason.SPORT_ANIMALS -> writeCheckBox(77f, 356f)
      Reason.CONVOCATION -> writeCheckBox(77f, 292f)
      Reason.MISSIONS -> writeCheckBox(77f, 254f)
      Reason.CHILDREN -> writeCheckBox(77f, 209f)
    }
  }

  fun writeSignQRCode(qrCodeContent: String) {
    QRCode
      .from(qrCodeContent)
      .withSize(120, 120)
      .withHint(EncodeHintType.MARGIN, 0)
      .stream()
      .toByteArray()
      .let {
        PDImageXObject.createFromByteArray(document, it, null)
      }
      .let {
        contentStream.drawImage(it, 430f, 95f)
      }
  }

  fun writeBigQRCode(qrCodeContent: String) {
    QRCode
      .from(qrCodeContent)
      .withSize(300, 300)
      .withHint(EncodeHintType.MARGIN, 0)
      .stream()
      .toByteArray()
      .let {
        PDImageXObject.createFromByteArray(document, it, null)
      }
      .let {
        contentStream.drawImage(it, 50f, 500f)
      }
  }

  private fun writeText(text: String, x: Float, y: Float) {
    with(contentStream) {
      beginText()
      setFont(PDType1Font.HELVETICA, 12f)
      newLineAtOffset(x, y)
      showText(text)
      endText()
    }
  }

  private fun writeCheckBox(x: Float, y: Float) {
    with(contentStream) {
      beginText()
      setFont(PDType1Font.HELVETICA, 20f)
      newLineAtOffset(x, y)
      showText("x")
      endText()
    }
  }
}
