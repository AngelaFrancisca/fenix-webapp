package pt.ist.fenix.webapp;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.fenixedu.academic.domain.ExecutionCourse;
import org.fenixedu.academic.domain.ExecutionSemester;
import org.fenixedu.academic.domain.Project;
import org.fenixedu.academic.domain.onlineTests.DistributedTest;
import org.fenixedu.academic.domain.onlineTests.OnlineTest;
import org.fenixedu.bennu.core.groups.Group;
import org.fenixedu.bennu.scheduler.CronTask;
import org.fenixedu.bennu.scheduler.annotation.Task;
import org.joda.time.YearMonthDay;

@Task(englishTitle = "Send daily test and project notifications")
public class SendDailyEvaluationAlert extends CronTask {
    @Override
    public void runTask() throws Exception {
        final YearMonthDay today = new YearMonthDay();
        final String tests = ExecutionSemester.readActualExecutionSemester().getAssociatedExecutionCoursesSet().stream()
                .flatMap(ec -> ec.getAssociatedEvaluationsSet().stream())
                .filter(e -> e instanceof OnlineTest)
                .map(e -> (OnlineTest) e)
                .map(ot -> ot.getDistributedTest())
                .filter(dt -> dt.getEndDateDateYearMonthDay().equals(today))
                .map(dt -> describe(today, dt))
                .sorted()
                .collect(Collectors.joining("\n"));

        final String projects = ExecutionSemester.readActualExecutionSemester().getAssociatedExecutionCoursesSet().stream()
                .flatMap(ec -> ec.getAssociatedEvaluationsSet().stream())
                .filter(e -> e instanceof Project)
                .map(e -> (Project) e)
                .filter(p -> p.getProjectEndDateTime().toYearMonthDay().equals(today))
                .map(p -> describe(today, p))
                .sorted()
                .collect(Collectors.joining("\n"));

        taskLog("%s%n", "Tests:\n" + tests + "\n\nProjects:\n" + projects);

        //to prevent duplicate emails to RT system
        final Stream<String> bccs = Group.managers().getMembers()
                .filter(user -> !user.getName().equalsIgnoreCase("fenix"))
                .map(user -> user.getEmail());
        org.fenixedu.messaging.core.domain.Message.fromSystem()
                .singleBcc(bccs)
                .singleTos("dsi.testesfenix@tecnico.ulisboa.pt")
                .subject("Testes e Projetos no Fénix " + today.toString("yyyy-MM-dd"))
                .textBody("Tests:\n" + tests + "\n\nProjects:\n" + projects)
                .send();
    }

    private String describe(final YearMonthDay today, final DistributedTest dt) {
        return describe(today,
                dt.getBeginDateDateYearMonthDay(),
                dt.getBeginHourDateHourMinuteSecond().toString("HH:mm"),
                dt.getEndHourDateHourMinuteSecond().toString("HH:mm"),
                dt.getOnlineTest().getAssociatedExecutionCoursesSet());
    }

    private String describe(final YearMonthDay today, final Project p) {
        return describe(today,
                p.getProjectBeginDateTime().toYearMonthDay(),
                p.getProjectBeginDateTime().toString("HH:mm"),
                p.getProjectEndDateTime().toString("HH:mm"),
                p.getAssociatedExecutionCoursesSet());
    }

    private String describe(final YearMonthDay today, final YearMonthDay evalStartDate, final String evalStartHour,
                            final String evalEndHour, final Set<ExecutionCourse> courses) {
        final String courseNames = courses.stream()
                .map(ec -> ec.getName() + " " + ec.getDegreePresentationString())
                .collect(Collectors.joining( ", " ));
        final long studentCount = courses.stream()
                .flatMap(c -> c.getAttendsSet().stream())
                .count();

        final StringBuilder builder = new StringBuilder("   ");
        if (today.equals(evalStartDate)) {
            builder.append(evalStartHour);
        } else {
            builder.append("00:00");
        }
        builder.append(" -> ");
        builder.append(evalEndHour);
        builder.append("\t");
        builder.append(studentCount);
        builder.append(" students");
        builder.append("\t ");
        builder.append(courseNames);
        return builder.toString();
    }

}
