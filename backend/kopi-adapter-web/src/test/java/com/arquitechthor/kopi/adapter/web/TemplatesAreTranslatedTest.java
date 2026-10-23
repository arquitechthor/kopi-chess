package com.arquitechthor.kopi.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.attoparser.ParseException;
import org.attoparser.config.ParseConfiguration;
import org.attoparser.dom.DOMMarkupParser;
import org.attoparser.dom.Element;
import org.attoparser.dom.INestableNode;
import org.attoparser.dom.INode;
import org.attoparser.dom.Text;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/// No template shows text that is not translated (design §11.4): every word
/// a person reads comes from the message bundles through `#{...}`.
///
/// The templates are read with AttoParser, the parser Thymeleaf itself uses.
/// A text with letters is fine only inside an element whose content
/// Thymeleaf replaces (`th:text`, `th:utext`, `th:replace`…): there it is
/// just the prototype seen when the file is opened as plain HTML. Visible
/// attributes (`title`, `alt`, `placeholder`, `aria-label`) need their
/// `th:` version for the same reason.
class TemplatesAreTranslatedTest {

    private static final Path TEMPLATES = Path.of("src", "main", "resources", "templates");

    /// Attributes whose value replaces the element's content.
    private static final Set<String> REPLACING = Set.of("th:text", "th:utext", "th:replace", "th:insert");

    /// Attributes people read (or hear, with a screen reader).
    private static final List<String> VISIBLE_ATTRIBUTES = List.of("title", "alt", "placeholder", "aria-label");

    /// Elements whose content is not text for people.
    private static final Set<String> NOT_TEXT = Set.of("script", "style");

    /// Words that are the same in every language: the language codes of the
    /// switch.
    private static final Set<String> UNIVERSAL = Set.of("EN", "ES");

    private static final Pattern LETTER = Pattern.compile("\\p{L}");

    static Stream<Path> templates() throws IOException {
        return Files.walk(TEMPLATES).filter(path -> path.toString().endsWith(".html")).sorted();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("templates")
    void everyVisibleTextIsTranslated(Path template) throws IOException, ParseException {
        var document = new DOMMarkupParser(ParseConfiguration.htmlConfiguration())
                .parse(template.toString(), new StringReader(Files.readString(template)));
        var problems = new ArrayList<String>();
        check(document, false, problems);

        assertThat(problems).as("untranslated text in %s", template).isEmpty();
    }

    @Test
    void theCheckFindsLiteralTextWhenThereIsSome() throws ParseException {
        // Guards the test itself: the same walk over a literal paragraph and a
        // literal title must complain, so a broken walk cannot pass silently.
        var document = new DOMMarkupParser(ParseConfiguration.htmlConfiguration())
                .parse("sample", new StringReader("<p>Hello</p><span th:text=\"#{x}\">Hi</span><b title=\"Hey\">·</b>"));
        var problems = new ArrayList<String>();
        check(document, false, problems);

        assertThat(problems).hasSize(2);
        assertThat(problems.get(0)).startsWith("text \"Hello\"");
        assertThat(problems.get(1)).startsWith("title=\"Hey\"");
    }

    /// Where a node is, for the failure message: AttoParser's DOM gives the
    /// line (it does not report the column).
    private static String where(INode node) {
        return "(line " + node.getLine() + ")";
    }

    private static void check(INestableNode node, boolean replaced, List<String> problems) {
        for (var child : node.getChildren()) {
            switch (child) {
                case Text text when !replaced -> {
                    var content = text.getContent().strip();
                    if (LETTER.matcher(content).find() && !UNIVERSAL.contains(content)) {
                        problems.add("text \"" + content + "\" " + where(text));
                    }
                }
                case Element element -> {
                    var name = element.getElementName().toLowerCase();
                    for (var attribute : VISIBLE_ATTRIBUTES) {
                        var value = element.getAttributeValue(attribute);
                        if (value != null && LETTER.matcher(value).find() && !element.hasAttribute("th:" + attribute)) {
                            problems.add(attribute + "=\"" + value + "\" " + where(element));
                        }
                    }
                    var contentReplaced = replaced || NOT_TEXT.contains(name)
                            || REPLACING.stream().anyMatch(element::hasAttribute);
                    check(element, contentReplaced, problems);
                }
                default -> {} // comments, doctype, text inside replaced content
            }
        }
    }
}
