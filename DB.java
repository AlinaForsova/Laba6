import org.jooq.*;
import org.jooq.impl.DSL;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.table.DefaultTableModel;

import static org.jooq.impl.DSL.*;

public class DB {
    private void ensureRolesExist() {
        try (Connection conn = DriverManager.getConnection(DB_URL, "postgres", "0000")) {
            DSLContext tempCreate = DSL.using(conn, SQLDialect.POSTGRES);
            tempCreate.execute("DO $$ BEGIN " +
                    "IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'admin') THEN " +
                    "CREATE ROLE admin WITH LOGIN PASSWORD '0000'; " +
                    "GRANT ALL PRIVILEGES ON DATABASE " + DB_NAME + " TO admin; " +
                    "END IF; END $$;");
            tempCreate.execute("DO $$ BEGIN " +
                    "IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'guest') THEN " +
                    "CREATE ROLE guest WITH LOGIN PASSWORD '1111'; " +
                    "GRANT CONNECT ON DATABASE " + DB_NAME + " TO guest; " +
                    "GRANT USAGE ON SCHEMA public TO guest; " +
                    "GRANT SELECT ON ALL TABLES IN SCHEMA public TO guest; " +
                    "END IF; END $$;");
        } catch (SQLException e) {
            System.err.println("Ошибка при создании ролей: " + e.getMessage());
        }
    }

    private static final String DB_URL = "jdbc:postgresql://localhost:5432/";
    private static final String DB_NAME = "Laba6";
    private static final String FULL_DB_URL = DB_URL + DB_NAME;

    private Connection connection;
    private DSLContext create;
    private String userRole = "guest";

    private JTextField firstNameField;
    private JTextField lastNameField;
    private JTextField birthDateField;
    private JTextField totalScoreField;
    private JCheckBox enrolledCheckBox;
    private JTable abiturientsTable;
    private DefaultTableModel tableModel;

    public DB(String username, String password) {
        ensureRolesExist();
        try {
            connection = DriverManager.getConnection(FULL_DB_URL, username, password);
            create = DSL.using(connection, SQLDialect.POSTGRES);

            this.userRole = username.equals("admin") ? "admin" : "guest";
            System.out.println("Вход как " + userRole);

            if (!tableExists("abiturients") && userRole.equals("admin")) {
                create.execute("CREATE TABLE abiturients (" +
                        "id SERIAL PRIMARY KEY, " +
                        "first_name TEXT NOT NULL, " +
                        "last_name TEXT NOT NULL, " +
                        "birth_date DATE NOT NULL, " +
                        "total_score INT NOT NULL, " +
                        "enrolled BOOLEAN NOT NULL)");
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Ошибка входа: " + e.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private boolean tableExists(String tableName) throws SQLException {
        return create.meta().getTables(tableName).stream()
                .anyMatch(table -> table.getName().equalsIgnoreCase(tableName));
    }

    public void showGUI() {
        JFrame frame = new JFrame("Абитуриенты");
        frame.setSize(900, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new FlowLayout());

        JButton addButton = new JButton("Добавить");
        JButton searchButton = new JButton("Поиск");
        JButton updateButton = new JButton("Обновить абитуриента");
        JButton deleteButton = new JButton("Удалить");
        JButton refreshButton = new JButton("Обновить таблицу");
        JButton clearTableButton = new JButton("Очистить таблицу");
        JButton dropDbButton = new JButton("Удалить БД");
        JButton createUserButton = new JButton("Создать пользователя");

        firstNameField = new JTextField(10);
        lastNameField = new JTextField(10);
        birthDateField = new JTextField(10);
        totalScoreField = new JTextField(5);
        enrolledCheckBox = new JCheckBox("Зачислен");

        addButton.addActionListener(this::addAbiturient);
        searchButton.addActionListener(this::searchByLastName);
        deleteButton.addActionListener(this::deleteByLastName);
        refreshButton.addActionListener(this::refreshTable);
        clearTableButton.addActionListener(e -> clearTable());
        dropDbButton.addActionListener(e -> dropDatabase());
        createUserButton.addActionListener(this::createUser);
        updateButton.addActionListener(this::updateAbiturient);

        frame.add(new JLabel("Имя:"));
        frame.add(firstNameField);
        frame.add(new JLabel("Фамилия:"));
        frame.add(lastNameField);
        frame.add(new JLabel("Дата (YYYY-MM-DD):"));
        frame.add(birthDateField);
        frame.add(new JLabel("Баллы:"));
        frame.add(totalScoreField);
        frame.add(enrolledCheckBox);

        frame.add(addButton);
        frame.add(searchButton);
        frame.add(updateButton);
        frame.add(deleteButton);
        frame.add(refreshButton);
        frame.add(clearTableButton);
        frame.add(dropDbButton);
        frame.add(createUserButton);

        String[] columnNames = {"ID", "Имя", "Фамилия", "Дата", "Баллы", "Зачислен"};
        tableModel = new DefaultTableModel(columnNames, 0);
        abiturientsTable = new JTable(tableModel);
        JScrollPane scrollPane = new JScrollPane(abiturientsTable);
        scrollPane.setPreferredSize(new Dimension(850, 300));
        frame.add(scrollPane);

        frame.setVisible(true);
        refreshTable(null);
    }

    private void addAbiturient(ActionEvent e) {
        try {
            String firstName = firstNameField.getText();
            String lastName = lastNameField.getText();
            String birthDateStr = birthDateField.getText();
            int totalScore = Integer.parseInt(totalScoreField.getText());
            boolean enrolled = enrolledCheckBox.isSelected();

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date parsedDate = sdf.parse(birthDateStr);
            java.sql.Date birthDate = new java.sql.Date(parsedDate.getTime());

            create.insertInto(table("abiturients"),
                            field("first_name"), field("last_name"), field("birth_date"), field("total_score"), field("enrolled"))
                    .values(firstName, lastName, birthDate, totalScore, enrolled)
                    .execute();

            JOptionPane.showMessageDialog(null, "Абитуриент добавлен!");
            refreshTable(null);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null, "Ошибка: " + ex.getMessage());
        }
    }

    public void dropDatabase() {
        if (!userRole.equals("admin")) {
            JOptionPane.showMessageDialog(null, "Только администратор может удалить базу данных!", "Ошибка", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try (Connection conn = DriverManager.getConnection(DB_URL, "postgres", "0000")) {
            DSLContext tempCreate = DSL.using(conn, SQLDialect.POSTGRES);
            tempCreate.execute("DROP DATABASE " + DB_NAME);
            JOptionPane.showMessageDialog(null, "База данных удалена!");
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Ошибка при удалении базы данных: " + e.getMessage());
        }
    }

    public void clearTable() {
        try {
            create.execute("DELETE FROM abiturients");
            JOptionPane.showMessageDialog(null, "Таблица очищена.");
            refreshTable(null);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Ошибка при очистке таблицы: " + e.getMessage());
        }
    }

    private void updateAbiturient(ActionEvent e) {
        try {
            String idStr = JOptionPane.showInputDialog("Введите ID абитуриента для обновления:");
            if (idStr == null || idStr.isEmpty()) return;
            int id = Integer.parseInt(idStr);

            String firstName = JOptionPane.showInputDialog("Введите новое имя:");
            String lastName = JOptionPane.showInputDialog("Введите новую фамилию:");
            String birthDateStr = JOptionPane.showInputDialog("Введите новую дату рождения (YYYY-MM-DD):");
            String totalScoreStr = JOptionPane.showInputDialog("Введите новый общий балл:");
            String enrolledStr = JOptionPane.showInputDialog("Зачислен? (true/false):");

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date parsedDate = sdf.parse(birthDateStr);
            java.sql.Date birthDate = new java.sql.Date(parsedDate.getTime());
            int totalScore = Integer.parseInt(totalScoreStr);
            boolean enrolled = Boolean.parseBoolean(enrolledStr);

            create.update(table("abiturients"))
                    .set(field("first_name"), firstName)
                    .set(field("last_name"), lastName)
                    .set(field("birth_date"), birthDate)
                    .set(field("total_score"), totalScore)
                    .set(field("enrolled"), enrolled)
                    .where(field("id").eq(id))
                    .execute();

            JOptionPane.showMessageDialog(null, "Абитуриент обновлен!");
            refreshTable(null); // Обновляем таблицу
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null, "Ошибка при обновлении абитуриента: " + ex.getMessage());
        }
    }

    private void createUser(ActionEvent e) {
        if (!userRole.equals("admin")) {
            JOptionPane.showMessageDialog(null, "Только администратор может создавать пользователей!", "Ошибка", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String username = JOptionPane.showInputDialog("Введите имя пользователя:");
        if (username == null || username.isEmpty()) {
            JOptionPane.showMessageDialog(null, "Имя пользователя не может быть пустым!", "Ошибка", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String password = JOptionPane.showInputDialog("Введите пароль:");
        if (password == null || password.isEmpty()) {
            JOptionPane.showMessageDialog(null, "Пароль не может быть пустым!", "Ошибка", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int isAdminOption = JOptionPane.showConfirmDialog(null, "Сделать пользователя администратором?", "Роль", JOptionPane.YES_NO_OPTION);
        boolean isAdmin = (isAdminOption == JOptionPane.YES_OPTION);

        try (Connection conn = DriverManager.getConnection(FULL_DB_URL, "postgres", "0000")) {
            DSLContext tempCreate = DSL.using(conn, SQLDialect.POSTGRES);

            tempCreate.execute("CREATE ROLE " + username + " WITH LOGIN ENCRYPTED PASSWORD '" + password + "'");

            if (isAdmin) {

                tempCreate.execute("GRANT ALL PRIVILEGES ON DATABASE " + DB_NAME + " TO " + username);
                tempCreate.execute("GRANT USAGE, SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO " + username);
                tempCreate.execute("GRANT USAGE, SELECT, UPDATE, INSERT, DELETE ON ALL SEQUENCES IN SCHEMA public TO " + username);
                JOptionPane.showMessageDialog(null, "Администратор " + username + " создан!");
            } else {

                tempCreate.execute("GRANT CONNECT ON DATABASE " + DB_NAME + " TO " + username);
                tempCreate.execute("GRANT USAGE ON SCHEMA public TO " + username);
                tempCreate.execute("GRANT SELECT ON ALL TABLES IN SCHEMA public TO " + username);
                JOptionPane.showMessageDialog(null, "Пользователь " + username + " создан с правами ТОЛЬКО для чтения!");
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(null, "Ошибка при создании пользователя: " + ex.getMessage());
        }
    }

    private void deleteByLastName(ActionEvent e) {
        String lastName = JOptionPane.showInputDialog("Введите фамилию для удаления:");
        if (lastName == null || lastName.isEmpty()) return;

        create.deleteFrom(table("abiturients")).where(field("last_name").eq(lastName)).execute();
        JOptionPane.showMessageDialog(null, "Удалено!");
        refreshTable(null);
    }

    private void searchByLastName(ActionEvent e) {
        String lastName = JOptionPane.showInputDialog("Введите фамилию:");
        if (lastName == null || lastName.isEmpty()) return;

        Result<Record> result = create.select().from("abiturients").where(field("last_name").eq(lastName)).fetch();
        if (result.isEmpty()) {
            JOptionPane.showMessageDialog(null, "Ничего не найдено!");
        } else {
            StringBuilder sb = new StringBuilder();
            for (Record record : result) {
                sb.append(record.get("first_name")).append(" ")
                        .append(record.get("last_name")).append(" ")
                        .append(record.get("birth_date")).append(" ")
                        .append(record.get("total_score")).append(" ")
                        .append(record.get("enrolled")).append("\n");
            }
            JOptionPane.showMessageDialog(null, sb.toString());
        }
    }

    private void refreshTable(ActionEvent e) {
        tableModel.setRowCount(0);
        Result<Record> records = create.select().from(table("abiturients")).fetch();
        for (Record record : records) {
            tableModel.addRow(new Object[]{
                    record.get("id"),
                    record.get("first_name"),
                    record.get("last_name"),
                    record.get("birth_date"),
                    record.get("total_score"),
                    record.get("enrolled")
            });
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(DB::showLoginScreen);
    }

    private static void showLoginScreen() {
        JFrame loginFrame = new JFrame("Вход");
        loginFrame.setSize(300, 200);
        loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        loginFrame.setLayout(new GridLayout(3, 2));

        JTextField userField = new JTextField();
        JPasswordField passField = new JPasswordField();
        JButton loginButton = new JButton("Войти");

        loginFrame.add(new JLabel("Логин:"));
        loginFrame.add(userField);
        loginFrame.add(new JLabel("Пароль:"));
        loginFrame.add(passField);
        loginFrame.add(loginButton);

        loginButton.addActionListener(e -> {
            String username = userField.getText();
            String password = new String(passField.getPassword());

            new DB(username, password).showGUI();
            loginFrame.dispose();
        });

        loginFrame.setVisible(true);
    }
}
