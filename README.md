# opencv-visual-assert

Libreria Maven per confronti visuali basati su OpenCV.

## Scope

La libreria riceve immagini prodotte dal progetto chiamante e restituisce risultati strutturati. Non apre browser, non esegue test e non dipende da runner o framework E2E.

## Dipendenza Maven

```xml
<dependency>
    <groupId>it.aruba.qaa</groupId>
    <artifactId>opencv-visual-assert</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

## Baseline

Directory baseline supportate:

```text
cv_img
resources/test/cv_img
Resources/test/cv_img
src/test/resources/cv_img
```

Nel progetto E2E la posizione consigliata e':

```text
resources/test/cv_img
```

Esempio:

```text
resources/test/cv_img/checkout-summary.png
```

La chiave `checkout-summary` risolve automaticamente:

```text
cv_img/checkout-summary.png
cv_img/checkout-summary.jpg
cv_img/checkout-summary.jpeg
resources/test/cv_img/checkout-summary.png
Resources/test/cv_img/checkout-summary.png
src/test/resources/cv_img/checkout-summary.png
```

## Uso Con Selenium

Il test Selenium naviga normalmente fino alla pagina, poi passa alla libreria lo screenshot prodotto dal driver.

```java
import it.aruba.qaa.cv.VisualAssert;
import it.aruba.qaa.cv.VisualCompareOptions;
import it.aruba.qaa.cv.VisualCompareResult;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

byte[] screenshotBytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);

VisualCompareResult result = VisualAssert.compareScreenshotBytes(
        screenshotBytes,
        "checkout-summary",
        VisualCompareOptions.builder()
                .artifactName("checkout-summary")
                .maxDiffPercent(0.25)
                .pixelTolerance(12)
                .build()
);

if (!result.passed()) {
    throw new AssertionError(result.failureMessage());
}
```

Con AssertJ:

```java
assertThat(result.passed())
        .as(result.failureMessage())
        .isTrue();
```

Con TestNG:

```java
Assert.assertTrue(result.passed(), result.failureMessage());
```

## Flusso E2E

```text
1. Selenium apre la pagina
2. il test aspetta che la UI sia stabile
3. Selenium produce lo screenshot
4. opencv-visual-assert carica la baseline da cv_img
5. OpenCV confronta baseline e screenshot
6. il test fallisce se la differenza supera le soglie configurate
```

## Output

Directory output predefinita:

```text
target/visual-assert
```

Artifact:

```text
<artifactName>-actual.png
<artifactName>-diff.png
```

Il progetto chiamante gestisce `passed()`, `failureMessage()` e i path degli artifact.

## Template Matching

```java
import it.aruba.qaa.cv.OpenCvVision;
import it.aruba.qaa.cv.TemplateMatchOptions;
import it.aruba.qaa.cv.TemplateMatchResult;

TemplateMatchResult result = OpenCvVision.findTemplateInScreenshotBytes(
        screenshotBytes,
        "checkout-button-icon",
        TemplateMatchOptions.builder()
                .artifactName("checkout-button-icon")
                .minScore(0.92)
                .build()
);

if (!result.found()) {
    throw new AssertionError(result.failureMessage());
}
```

## Soglie

```java
VisualCompareOptions options = VisualCompareOptions.builder()
        .artifactName("checkout-summary")
        .maxDiffPercent(0.25)
        .pixelTolerance(12)
        .build();
```

`pixelTolerance` gestisce piccole variazioni colore per antialiasing, font rendering e differenze browser.

`maxDiffPercent` definisce la percentuale massima di pixel differenti ammessa.
