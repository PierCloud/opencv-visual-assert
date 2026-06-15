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

## Runtime OpenCV

La dipendenza OpenCV e' dichiarata `provided` per non propagare automaticamente i jar nativi nei progetti chiamanti.

Nel progetto che esegue i test visuali va resa disponibile nel profilo/job dedicato:

```xml
<dependency>
    <groupId>org.bytedeco</groupId>
    <artifactId>opencv-platform</artifactId>
    <version>4.13.0-1.5.13</version>
    <scope>test</scope>
</dependency>
```

La scansione di sicurezza globale dovrebbe analizzare il framework senza il profilo visuale. Il profilo visuale va eseguito in ambiente controllato, con browser, viewport, font e runtime OpenCV stabili.

## Baseline

Directory baseline supportata:

```text
test/cv_img
```

Nel progetto E2E la baseline deve essere disponibile nel classpath come:

```text
test/cv_img
```

Esempio:

```text
test/cv_img/checkout-summary.png
```

La chiave `checkout-summary` risolve automaticamente:

```text
test/cv_img/checkout-summary.png
test/cv_img/checkout-summary.jpg
test/cv_img/checkout-summary.jpeg
```

## Uso

Il progetto chiamante produce lo screenshot e passa alla libreria i bytes dell'immagine.

```java
import it.aruba.qaa.cv.VisualAssert;
import it.aruba.qaa.cv.VisualCompareOptions;
import it.aruba.qaa.cv.VisualCompareResult;

byte[] screenshotBytes = captureScreenshotBytes();

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

Per salvare una baseline:

```java
import it.aruba.qaa.cv.VisualAssertUtil;

byte[] screenshotBytes = captureScreenshotBytes();
VisualAssertUtil.saveBaselineScreenshot(screenshotBytes, "access-hed-page");
```

Per confrontare usando le opzioni predefinite della utility:

```java
import it.aruba.qaa.cv.VisualAssertUtil;
import it.aruba.qaa.cv.VisualCompareResult;

byte[] screenshotBytes = captureScreenshotBytes();
VisualCompareResult result = VisualAssertUtil.compareScreenshot(screenshotBytes, "access-hed-page");
```

## Flusso E2E

```text
1. il progetto chiamante porta la UI nello stato da verificare
2. il test aspetta che la UI sia stabile
3. il progetto chiamante produce lo screenshot
4. opencv-visual-assert carica la baseline da test/cv_img
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
<artifactName>-expected.png
<artifactName>-actual.png
<artifactName>-diff.png
<artifactName>-report.html
```

Il progetto chiamante gestisce `passed()`, `failureMessage()` e i path degli artifact.

Il file `*-diff.png` evidenzia in rosso i pixel differenti. Il file `*-report.html` mostra baseline, actual e diff affiancati con riepilogo numerico.

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
        .writeHtmlReport(true)
        .build();
```

`pixelTolerance` gestisce piccole variazioni colore per antialiasing, font rendering e differenze browser.

`maxDiffPercent` definisce la percentuale massima di pixel differenti ammessa.
