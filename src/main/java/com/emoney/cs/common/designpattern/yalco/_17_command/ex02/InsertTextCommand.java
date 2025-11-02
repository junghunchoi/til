package com.emoney.cs.common.designpattern.yalco._17_command.ex02;

public class InsertTextCommand implements Command {
    private TextEditor editor;
    private String text;
    private int position;

    public InsertTextCommand(
            TextEditor editor, String text, int position)
    {
        this.editor = editor;
        this.text = text;
        this.position = position;
    }

    @Override
    public void execute() {
        editor.insertText(text, position);
    }

    @Override
    public void undo() {
        editor.deleteText(position, text.length());
    }
}