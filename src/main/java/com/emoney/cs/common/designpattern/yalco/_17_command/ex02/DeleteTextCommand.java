package com.emoney.cs.common.designpattern.yalco._17_command.ex02;

public class DeleteTextCommand implements Command {
    private TextEditor editor;
    private String deletedText;
    private int position;

    public DeleteTextCommand(
            TextEditor editor, int position, int length)
    {
        this.editor = editor;
        this.position = position;
        this.deletedText = editor.getTextSubstring(
                position, position + length);
    }

    @Override
    public void execute() {
        editor.deleteText(position, deletedText.length());
    }

    @Override
    public void undo() {
        editor.insertText(deletedText, position);
    }
}