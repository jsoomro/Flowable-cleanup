package com.company.flowable.ops;

import java.io.Console;
import java.io.IOException;
import java.util.Scanner;

public class ConsolePrompt {
    private final Console console = System.console();
    private final Scanner scanner = console == null ? new Scanner(System.in) : null;

    public String ask(String prompt) throws IOException {
        if (console != null) {
            return console.readLine(prompt + " ");
        }
        System.out.print(prompt + " ");
        return scanner.nextLine();
    }
}
