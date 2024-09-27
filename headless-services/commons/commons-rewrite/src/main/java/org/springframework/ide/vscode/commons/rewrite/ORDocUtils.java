/*******************************************************************************
 * Copyright (c) 2023, 2024 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.rewrite;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.lsp4j.AnnotatedTextEdit;
import org.eclipse.lsp4j.CreateFile;
import org.eclipse.lsp4j.DeleteFile;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.openrewrite.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.languageserver.completion.DocumentEdits;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleTextDocumentService;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.springframework.ide.vscode.commons.util.text.IRegion;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

public class ORDocUtils {
		
	private static final Logger log = LoggerFactory.getLogger(ORDocUtils.class);
	
	public static Optional<DocumentEdits> computeEdits(IDocument doc, Result result) {
			TextDocument newDoc = new TextDocument(null, LanguageId.PLAINTEXT, 0, result.getAfter().printAll());

			EditList diff = JGitUtils.getDiff(result.getBefore().printAll(), newDoc.get());
			if (!diff.isEmpty()) {
				DocumentEdits edits = new DocumentEdits(doc, false);
				for (Edit e : diff) {
					try {
						switch(e.getType()) {
						case DELETE:
							edits.delete(doc.getLineOffset(e.getBeginA()), getStartOfLine(doc, e.getEndA()));
							break;
						case INSERT:
							edits.insert(doc.getLineOffset(e.getBeginA()), newDoc.textBetween(newDoc.getLineOffset(e.getBeginB()), getStartOfLine(newDoc, e.getEndB())));
							break;
						case REPLACE:
							edits.replace(doc.getLineOfOffset(e.getBeginA()), getStartOfLine(doc, e.getEndA()), newDoc.textBetween(newDoc.getLineOffset(e.getBeginB()), getStartOfLine(newDoc, e.getEndB())));
							break;
						case EMPTY:
							break;
						}
					} catch (BadLocationException ex) {
						log.error("Diff conversion failed", ex);
					}
				}
				return Optional.of(edits);
			}
		return Optional.empty();	

	}
	
	public static Optional<TextDocumentEdit> computeTextDocEdit(TextDocument doc, String oldContent, String newContent, String changeAnnotationId) {
		TextDocument newDoc = new TextDocument(null, LanguageId.PLAINTEXT, 0, newContent);

		EditList diff = JGitUtils.getDiff(oldContent, newDoc.get());
		if (!diff.isEmpty()) {
			TextDocumentEdit edit = new TextDocumentEdit();
			edit.setTextDocument(new VersionedTextDocumentIdentifier(doc.getUri(), doc.getVersion()));
			List<TextEdit> textEdits = new ArrayList<>();
			edit.setEdits(textEdits);
			for (Edit e : diff) {
				try {
					switch(e.getType()) {
					case DELETE:
						AnnotatedTextEdit textEdit = new AnnotatedTextEdit();
						int start = doc.getLineOffset(e.getBeginA());
						int end = getStartOfLine(doc, e.getEndA());
						textEdit.setRange(new Range(doc.toPosition(start), doc.toPosition(end)));
						textEdit.setNewText("");
						textEdit.setAnnotationId(changeAnnotationId);
						textEdits.add(textEdit);
						break;
					case INSERT:
						textEdit = new AnnotatedTextEdit();
						Position position = doc.toPosition(doc.getLineOffset(e.getBeginA()));
						textEdit.setRange(new Range(position, position));
						textEdit.setNewText(newDoc.textBetween(newDoc.getLineOffset(e.getBeginB()), getStartOfLine(newDoc, e.getEndB())));
						textEdit.setAnnotationId(changeAnnotationId);
						textEdits.add(textEdit);
						break;
					case REPLACE:
						textEdit = new AnnotatedTextEdit();
						start = doc.getLineOffset(e.getBeginA());
						end = getStartOfLine(doc, e.getEndA());
						textEdit.setRange(new Range(doc.toPosition(start), doc.toPosition(end)));
						textEdit.setNewText(newDoc.textBetween(newDoc.getLineOffset(e.getBeginB()), getStartOfLine(newDoc, e.getEndB())));
						textEdit.setAnnotationId(changeAnnotationId);
						textEdits.add(textEdit);
						break;
					case EMPTY:
						break;
					}
				} catch (BadLocationException ex) {
					log.error("Diff conversion failed", ex);
				}
			}
			System.out.println("edits "+ edit);
			return Optional.of(edit);
		}
		return Optional.empty();
	}
	
	public static Optional<TextDocumentEdit> computeSimpleTextDocEdit(TextDocument doc, Result result) {
		TextDocument newDoc = new TextDocument(null, LanguageId.PLAINTEXT, 0, result.getAfter().printAll());

		EditList diff = JGitUtils.getDiff(result.getBefore().printAll(), newDoc.get());
		if (!diff.isEmpty()) {
			TextDocumentEdit edit = new TextDocumentEdit();
			edit.setTextDocument(new VersionedTextDocumentIdentifier(doc.getUri(), doc.getVersion()));
			TextEdit te = new TextEdit();
			te.setNewText(result.getAfter().printAll());
			try {
				te.setRange(new Range(new Position(0,0), doc.toPosition(doc.getLength())));
			} catch (BadLocationException e) {
				// ignore
			}
			edit.setEdits(List.of(te));
			return Optional.of(edit);
		}
		return Optional.empty();
	}
	
	private static int getStartOfLine(IDocument doc, int lineNumber) {
		IRegion lineInformation = doc.getLineInformation(lineNumber);
		if (lineInformation != null) {
			return lineInformation.getOffset();
		}
		if (lineNumber > 0) {
			IRegion currentLine = doc.getLineInformation(lineNumber - 1);
			return currentLine.getOffset() + currentLine.getLength();
		}
		return 0;
	}
	
	public static Optional<WorkspaceEdit> createWorkspaceEdit(SimpleTextDocumentService documents, List<Result> results, String changeAnnotationId) {
		if (results.isEmpty()) {
			return Optional.empty();
		}
		WorkspaceEdit we = new WorkspaceEdit();
		we.setDocumentChanges(new ArrayList<>());
		for (Result result : results) {
			if (result.getBefore() == null) {
				String docUri = result.getAfter().getSourcePath().toUri().toASCIIString();
				createNewFileEdit(docUri, result.getAfter().printAll(), changeAnnotationId, we);
			} else if (result.getAfter() == null) {
				String docUri = result.getBefore().getSourcePath().toUri().toASCIIString();
				createDeleteFileEdit(docUri, we);
			} else {
				String docUri = result.getBefore().getSourcePath().toUri().toASCIIString();
				System.out.println(result.getBefore().getSourcePath().toString());
				createUpdateFileEdit(documents, docUri, result.getBefore().printAll(), result.getAfter().printAll(), changeAnnotationId, we);
			}
			
		}
		return Optional.of(we);
	}
	
	public static void createWorkspaceEdit(SimpleTextDocumentService documents, String docUri, String oldContent, String newContent, String changeAnnotationId, WorkspaceEdit we) {
		if(oldContent == null) {
			createNewFileEdit(docUri, newContent, changeAnnotationId, we);
		} else if (newContent == null) {
			createDeleteFileEdit(docUri, we);
		} else {
			createUpdateFileEdit(documents, docUri, oldContent, newContent, changeAnnotationId, we);
		}
	}
	
	private static void createNewFileEdit(String docUri, String newContent, String changeAnnotationId,
			WorkspaceEdit we) {
		CreateFile ro = new CreateFile();
		ro.setUri(docUri);
		we.getDocumentChanges().add(Either.forRight(ro));
		
		TextDocumentEdit te = new TextDocumentEdit();
		te.setTextDocument(new VersionedTextDocumentIdentifier(docUri, 0));
		Position cursor = new Position(0,0);
		te.setEdits(List.of(new AnnotatedTextEdit(new Range(cursor, cursor), newContent, changeAnnotationId)));
		we.getDocumentChanges().add(Either.forLeft(te));
	}
	
	private static void createDeleteFileEdit(String docUri, WorkspaceEdit we) {
		we.getDocumentChanges().add(Either.forRight(new DeleteFile(docUri)));
	}

	private static void createUpdateFileEdit(SimpleTextDocumentService documents, String docUri, String oldContent,
			String newContent, String changeAnnotationId, WorkspaceEdit we) {
		TextDocument doc = documents.getLatestSnapshot(docUri);
		if (doc == null) {
			doc = new TextDocument(docUri, null, 0, oldContent);
			ORDocUtils.computeTextDocEdit(doc, oldContent, newContent, changeAnnotationId).ifPresent(te -> we.getDocumentChanges().add(Either.forLeft(te)));
		} else {
			ORDocUtils.computeTextDocEdit(doc, oldContent, newContent, changeAnnotationId).ifPresent(te -> we.getDocumentChanges().add(Either.forLeft(te)));
		}
	}
	
}
