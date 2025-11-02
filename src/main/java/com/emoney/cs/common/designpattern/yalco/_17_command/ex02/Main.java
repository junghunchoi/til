package com.emoney.cs.common.designpattern.yalco._17_command.ex02;

// Client code
public class Main {
    public static void main(String[] args) {
        TextEditor editor = new TextEditor();
        TextEditorInvoker invoker = new TextEditorInvoker();

        Command insertHello = new InsertTextCommand(editor, "Hello, ", 0);
        invoker.executeCommand(insertHello);

        Command insertWorld = new InsertTextCommand(editor, "World!", 7);
        invoker.executeCommand(insertWorld);

        System.out.println("Current text: " + editor.getContent());

        invoker.undo();
        System.out.println("After undo: " + editor.getContent());

        invoker.redo();
        System.out.println("After redo: " + editor.getContent());

        Command deleteCommand = new DeleteTextCommand(editor, 0, 7);
        invoker.executeCommand(deleteCommand);
        System.out.println("After delete: " + editor.getContent());

        invoker.undo();
        System.out.println("Final text: " + editor.getContent());
    }
}