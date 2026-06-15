# visual-assert

Libreria Maven per confronti visuali custom in Java.

## Scope

La libreria riceve immagini prodotte dal progetto chiamante e restituisce risultati strutturati. Non apre browser, non esegue test e non dipende da runner o framework E2E.

## Dipendenza Maven

```xml
<dependency>
    <groupId>it.aruba.qaa</groupId>
    <artifactId>visual-assert</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

## Dipendenze

La libreria non dichiara dipendenze esterne. Il motore visuale usa solo API Java standard.

## Baseline

Directory baseline supportata:

```text
test/cv_img
```

Nel progetto E2E la baseline deve trovarsi sotto:

```text
resources/test/cv_img
```

Esempio:

```text
resources/test/cv_img/checkout-summary.png
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

`passed()` torna `true` sia per `PASSED` sia per `WARNING`. Lo stato completo e' disponibile con:

```java
result.status();
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

Per confrontare solo una regione dell'immagine:

```java
import it.aruba.qaa.cv.VisualRegion;

VisualCompareResult result = VisualAssert.compareScreenshotBytes(
        screenshotBytes,
        "access-hed-page",
        VisualCompareOptions.builder()
                .artifactName("access-hed-page-login-box")
                .compareOnlyRegion(VisualRegion.of(500, 300, 700, 420))
                .maxDiffPercent(0.05)
                .pixelTolerance(12)
                .build()
);
```

`compareOnlyRegion` ritaglia baseline e actual prima del confronto. Se usata insieme a `ignoreRegion`, le coordinate di `ignoreRegion` sono relative alla regione ritagliata.

Per usare una chiamata completamente parametrica:

```java
import java.nio.file.Path;
import java.util.List;

VisualCompareResult result = VisualAssertUtil.compareScreenshotCustom(
        screenshotBytes,
        "access-hed-page",
        "access-hed-page-login-box",
        Path.of("target", "visual-assert"),
        0.05,
        0,
        0.80,
        1.25,
        12,
        VisualRegion.of(500, 300, 700, 420),
        List.of(),
        true,
        true,
        true,
        true
);
```

## Flusso E2E

```text
1. il progetto chiamante porta la UI nello stato da verificare
2. il test aspetta che la UI sia stabile
3. il progetto chiamante produce lo screenshot
4. visual-assert carica la baseline da test/cv_img
5. il motore visuale confronta baseline e screenshot
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

Il file `*-diff.png` evidenzia le aree considerate differenti. Il report distingue `PASSED`, `WARNING - NEAR THRESHOLD` e `FAILED`.

Il path del report e' disponibile dal risultato:

```java
result.htmlReport().ifPresent(path -> System.out.println("Visual report: " + path));
```

Il confronto normalizza le immagini, ridimensiona l'actual alla baseline quando necessario, lavora in scala di grigi, applica blur, differenza assoluta, soglia, morfologia e filtro sulle componenti isolate.

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

Il template matching usa correlazione normalizzata, ricerca a passo variabile e raffinamento locale del punto migliore.

## Soglie

```java
VisualCompareOptions options = VisualCompareOptions.builder()
        .artifactName("checkout-summary")
        .compareOnlyRegion(VisualRegion.of(500, 300, 700, 420))
        .maxDiffPercent(0.25)
        .warningThresholdRatio(0.80)
        .failureThresholdMultiplier(1.25)
        .pixelTolerance(12)
        .writeHtmlReport(true)
        .build();
```

`pixelTolerance` gestisce piccole variazioni colore per antialiasing, font rendering e differenze browser.

`maxDiffPercent` definisce la percentuale massima di area differente ammessa.

`compareOnlyRegion` limita il confronto a una sola area dell'immagine.

`warningThresholdRatio` definisce quando iniziare a segnalare warning rispetto alla soglia. Con `0.80`, un valore sopra l'80% della soglia entra in warning.

`failureThresholdMultiplier` definisce quando il warning diventa fallimento. Con `1.25`, il test fallisce solo quando supera del 25% la soglia configurata.
