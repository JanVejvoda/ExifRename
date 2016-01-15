package cz.jan.exifrename;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;
import java.util.HashSet;
import java.util.TimeZone;
import java.text.SimpleDateFormat;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifDirectoryBase;
import com.drew.metadata.exif.ExifSubIFDDirectory;

public class ExifRename extends SimpleFileVisitor<Path> {
	private Options options;
	private CommandLineParser parser;

	private File inputDir;
	private File outputDir;
	private boolean recursive = false;
	private SimpleDateFormat fileNameFormater;


	public ExifRename () {
		this.parser = new DefaultParser();

		this.options = new Options();
		this.options.addOption("f", "format", true, "Output file name format");
		this.options.addOption("in", "input-dir", true, "Input directory");
		this.options.addOption("out", "output-dir", true, "Output directory");
		this.options.addOption("R", "recursive", false, "Process directory recursivelly");
	}


	public void ParseArguments (String[] args) throws ParseException {
		CommandLine line = this.parser.parse(this.options, args);

		if (!line.hasOption("input-dir")) {
			throw new ParseException("Missing parameter input-dir!");
		}

		this.inputDir = new File(line.getOptionValue("input-dir"));

		if (!this.inputDir.exists()) {
			throw new ParseException("Directory " + this.inputDir.getAbsolutePath() + " doesn't exists");
		}

		if (line.hasOption("output-dir")) {
			this.outputDir = new File(line.getOptionValue("output-dir"));
		}
		else {
			this.outputDir = this.inputDir;
		}

		this.recursive = line.hasOption("recursive");

		if (!line.hasOption("format")) {
			this.fileNameFormater = new SimpleDateFormat("yyyyMMdd_HHmmss");
		}
		else {
			try {
				this.fileNameFormater = new SimpleDateFormat(line.getOptionValue("format"));
			}
			catch (Exception e) {
				throw new ParseException("Invalid format definition: " + e.getMessage());
			}
		}
	}


	private static void rename (File file, File outputDir, SimpleDateFormat fileNameFormater) {
		File newName = getNewName(file, outputDir, fileNameFormater);

		try {
			if (!newName.getParentFile().exists()) {
				try {
					newName.getParentFile().mkdirs();
				}
				catch (Exception e) {
					throw new ParseException("Can't create output directory: " + e.getMessage());
				}
			}
			
			Files.move(file.toPath(), newName.toPath());
			System.out.println(file.getAbsolutePath() + "\t->\t" + newName.getAbsolutePath());
		}
		catch (Exception e) {
			System.out.println("Can't move file: " + file.getAbsolutePath() + "\t->\t" + newName.getAbsolutePath());
			System.out.println("Err: " + e.getMessage());
		}
	}


	private static File getNewName (File file, File outputDir, SimpleDateFormat myFormater) {
		Date modified = null;
		String extension = file.getName().substring(file.getName().lastIndexOf("."));

		try {
			Metadata metadata = ImageMetadataReader.readMetadata(file);
			ExifSubIFDDirectory exifDirectory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);

			modified = exifDirectory.getDate(ExifDirectoryBase.TAG_DATETIME_ORIGINAL, TimeZone.getDefault());
		}
		catch (Exception e) {
			// nejsou metadata
		}

		modified = modified == null ? new Date(file.lastModified()) : modified;

		String newFileNAme = myFormater.format(modified);
		String year = String.format("%1$tY", modified);
		String month = String.format("%1$tm", modified);
		String day = String.format("%1$td", modified);

		File newFile = new File(outputDir, year);
		newFile = new File(newFile, month);
		newFile = new File(newFile, day);
		newFile = new File(newFile, newFileNAme + extension);
		
		if(newFile.exists()) {
			newFile = new File(outputDir, year);
			newFile = new File(newFile, "duplicates");
			newFile = new File(newFile, month);
			newFile = new File(newFile, day);
			newFile = new File(newFile, newFileNAme + extension + "_" + System.currentTimeMillis());
		}

		return newFile;
	}


	@Override
	public FileVisitResult visitFile (Path filePath, BasicFileAttributes attrs) throws IOException {

		if (attrs.isRegularFile()) {
			try {
				rename(filePath.toFile(), this.outputDir, this.fileNameFormater);
			}
			catch (Exception e) {
				System.out.println(e.getMessage());
			}
		}

		return FileVisitResult.CONTINUE;
	}


	public static void main (String[] args) {
		ExifRename renamer = new ExifRename();

		try {
			renamer.ParseArguments(args);

			if (renamer.recursive) {
				Files.walkFileTree(renamer.inputDir.toPath(), renamer);
			}
			else {
				HashSet<FileVisitOption> opts = new HashSet<>();
				Files.walkFileTree(renamer.inputDir.toPath(), opts, 1, renamer);
			}
		}
		catch (Exception e) {
			System.out.println(e.getMessage());
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("ExifRename --input-dir <arg> ..", renamer.options);
		}

	}

}
