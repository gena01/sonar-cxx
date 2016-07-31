/*
 * Sonar C++ Plugin (Community)
 * Copyright (C) 2010-2016 SonarOpenCommunity
 * http://github.com/SonarOpenCommunity/sonar-cxx
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.plugins.cxx.highlighter;

import javax.annotation.Nullable;
import com.sonar.sslr.api.AstAndTokenVisitor;
import com.sonar.sslr.api.AstNode;
import com.sonar.sslr.api.Grammar;
import com.sonar.sslr.api.Token;
import com.sonar.sslr.api.Trivia;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.highlighting.NewHighlighting;
import org.sonar.api.batch.sensor.highlighting.TypeOfText;
import org.sonar.squidbridge.SquidAstVisitor;
import org.sonar.cxx.api.CxxTokenType;
import org.sonar.cxx.api.CxxKeyword;

public class CxxHighlighter extends SquidAstVisitor<Grammar> implements AstAndTokenVisitor {

  private NewHighlighting newHighlighting;
  private final SensorContext context;

  private class TokenLocation {

    protected int startLine;
    protected int startLineOffset;
    protected int endLine;
    protected int endLineOffset;

    public TokenLocation(Token token) {
      startLine = token.getLine();
      startLineOffset = token.getColumn();
      endLine = this.startLine;
      endLineOffset = startLineOffset + token.getValue().length();
    }

    public int startLine() {
      return startLine;
    }

    public int startLineOffset() {
      return startLineOffset;
    }

    public int endLine() {
      return endLine;
    }

    public int endLineOffset() {
      return endLineOffset;
    }
  }

  private class CommentLocation extends TokenLocation {

    public CommentLocation(Token token) {
      super(token);
      String value = token.getValue();
      String[] lines = value.split("\r\n|\n|\r", -1);

      if (lines.length > 1) {
        endLine = token.getLine() + lines.length - 1;
        endLineOffset = lines[lines.length - 1].length();
      }
    }
  }

  private class PreprocessorDirectiveLocation extends TokenLocation {

    private final Pattern r = Pattern.compile("^[ \t]*#[ \t]*\\w+");
     
    PreprocessorDirectiveLocation(Token token) {
      super(token);
      Matcher m = r.matcher(token.getValue());
      if (m.find()) {
        endLineOffset = startLineOffset + (m.end() - m.start());
      } else {
        endLineOffset = startLineOffset;
      }
    }
  }
  
  public CxxHighlighter(SensorContext context) {
    this.context = context;
  }

  @Override
  public void visitFile(@Nullable AstNode astNode) {
    newHighlighting = context.newHighlighting();
    InputFile inputFile = context.fileSystem().inputFile(context.fileSystem().predicates().is(getContext().getFile().getAbsoluteFile()));
    newHighlighting.onFile(inputFile);
  }

  @Override
  public void leaveFile(@Nullable AstNode astNode) {
    newHighlighting.save();
  }

  @Override
  public void visitToken(Token token) {
    if (!token.isGeneratedCode()) {
      if (token.getType().equals(CxxTokenType.NUMBER)) {
        highlight(new TokenLocation(token), TypeOfText.CONSTANT);
      } else if (token.getType() instanceof CxxKeyword) {
        highlight(new TokenLocation(token), TypeOfText.KEYWORD);
      } else if (token.getType().equals(CxxTokenType.STRING)) {
        highlight(new TokenLocation(token), TypeOfText.STRING);
      } else if (token.getType().equals(CxxTokenType.CHARACTER)) {
        highlight(new TokenLocation(token), TypeOfText.STRING);
      }

      for (Trivia trivia : token.getTrivia()) {
        if (trivia.isComment()) {
          highlight(new CommentLocation(trivia.getToken()), TypeOfText.COMMENT);
        } else if (trivia.isSkippedText()) {
          if (trivia.getToken().getType() == CxxTokenType.PREPROCESSOR) {
            highlight(new PreprocessorDirectiveLocation(trivia.getToken()), TypeOfText.PREPROCESS_DIRECTIVE);
          }
        }
      }
    }
  }

  private void highlight(TokenLocation location, TypeOfText typeOfText) {
    newHighlighting.highlight(location.startLine(), location.startLineOffset(), location.endLine(), location.endLineOffset(), typeOfText);
  }

}
